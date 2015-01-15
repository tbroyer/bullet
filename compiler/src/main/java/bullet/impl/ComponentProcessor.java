/*
 * Copyright (C) 2014 Thomas Broyer
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package bullet.impl;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import javax.annotation.Generated;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.auto.common.SuperficialValidation;
import com.google.auto.service.AutoService;
import com.google.common.collect.Iterables;
import com.squareup.javawriter.ClassName;
import com.squareup.javawriter.ClassWriter;
import com.squareup.javawriter.ConstructorWriter;
import com.squareup.javawriter.JavaWriter;
import com.squareup.javawriter.MethodWriter;
import com.squareup.javawriter.ParameterizedTypeName;
import com.squareup.javawriter.TypeNames;
import com.squareup.javawriter.TypeVariableName;

import bullet.ObjectGraph;

@AutoService(Processor.class)
@SupportedAnnotationTypes(ComponentProcessor.DAGGER_COMPONENT)
public class ComponentProcessor extends AbstractProcessor {

  static final String DAGGER_COMPONENT = "dagger.Component";

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {

    Set<? extends Element> componentElements = roundEnv.getElementsAnnotatedWith(
        processingEnv.getElementUtils().getTypeElement(DAGGER_COMPONENT));

    for (Element element : componentElements) {
      if (SuperficialValidation.validateElement(element)) {
        TypeElement componentElement = MoreElements.asType(element);
        generateObjectGraph(componentElement);
      }
    }

    return false;
  }

  private void generateObjectGraph(TypeElement element) {
    ArrayList<ExecutableElement> provisionMethods = new ArrayList<>();
    ArrayList<ExecutableElement> membersInjectionMethods = new ArrayList<>();
    for (ExecutableElement method : ElementFilter.methodsIn(processingEnv.getElementUtils().getAllMembers(element))) {
      if (isComponentProvisionMethod(method)) {
        provisionMethods.add(method);
      } else if (isComponentMembersInjectionMethod(method)) {
        membersInjectionMethods.add(method);
      }
    }
    // Order members-injection methods from most-specific to least-specific types, for cascading ifs of instanceof.
    Collections.sort(membersInjectionMethods, new Comparator<ExecutableElement>() {
      final Types typeUtils = processingEnv.getTypeUtils();

      @Override
      public int compare(ExecutableElement o1, ExecutableElement o2) {
        TypeMirror p1 = Iterables.getOnlyElement(o1.getParameters()).asType(), p2 = Iterables.getOnlyElement(o2.getParameters()).asType();
        if (typeUtils.isSubtype(p1, p2)) {
          return -1;
        } else if (typeUtils.isSubtype(p2, p1)) {
          return 1;
        }
        return getName(p1).compareTo(getName(p2));
      }

      private String getName(TypeMirror type) {
        return MoreElements.asType(typeUtils.asElement(type)).getQualifiedName().toString();
      }
    });

    ClassName name = buildName(element);
    ClassWriter classWriter = ClassWriter.forClassName(name);
    classWriter.addOriginatingElement(element);
    classWriter.annotate(Generated.class)
        .setValue(ComponentProcessor.class.getCanonicalName());
    classWriter.addModifiers(Modifier.PUBLIC, Modifier.FINAL);
    classWriter.addImplementedType(ClassName.fromClass(ObjectGraph.class));

    classWriter.addField(element, "component")
        .addModifiers(Modifier.PRIVATE, Modifier.FINAL);

    ConstructorWriter ctorWriter = classWriter.addConstructor();
    ctorWriter.addModifier(Modifier.PUBLIC);
    ctorWriter.addParameter(element, "component");
    ctorWriter.body().addSnippet("this.component = component;");

    TypeVariableName t = TypeVariableName.create("T");
    MethodWriter getWriter = classWriter.addMethod(t, "get");
    getWriter.annotate(Override.class);
    getWriter.addModifier(Modifier.PUBLIC);
    getWriter.addTypeVariable(t);
    getWriter.addParameter(ParameterizedTypeName.create(Class.class, t), "type");
    for (ExecutableElement method : provisionMethods) {
      getWriter.body().addSnippet(
          "if (type == %s.class) {\n" +
              "  return type.cast(this.component.%s());\n" +
              "}",
          TypeNames.forTypeMirror(method.getReturnType()), method.getSimpleName());
    }
    // TODO: exception message
    getWriter.body().addSnippet("throw new %s();", ClassName.fromClass(IllegalArgumentException.class));

    MethodWriter injectWriter = classWriter.addMethod(t, "inject");
    injectWriter.annotate(Override.class);
    injectWriter.addModifier(Modifier.PUBLIC);
    injectWriter.addTypeVariable(t);
    injectWriter.addParameter(t, "instance");
    for (ExecutableElement method : membersInjectionMethods) {
      injectWriter.body().addSnippet(
          "if (instance instanceof %1$s) {\n" +
          "  this.component.%2$s((%1$s) instance);\n" +
          "  return instance;\n" +
          "}",
          TypeNames.forTypeMirror(Iterables.getOnlyElement(method.getParameters()).asType()), method.getSimpleName());
    }
    // TODO: exception message
    injectWriter.body().addSnippet("throw new %s();", ClassName.fromClass(IllegalArgumentException.class));

    try {
      JavaWriter.create()
          .addTypeWriter(classWriter)
          .writeTo(processingEnv.getFiler());
    } catch (IOException ioe) {
      StringWriter sw = new StringWriter();
      PrintWriter pw = new PrintWriter(sw);
      pw.println("Error generating source file for type " + name);
      ioe.printStackTrace(pw);
      pw.close();
      processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, sw.toString());
    }
  }

  private ClassName buildName(Element element) {
    String name = element.getSimpleName().toString();
    for (element = element.getEnclosingElement(); element.getKind() != ElementKind.PACKAGE; element = element.getEnclosingElement()) {
      name = element.getSimpleName() + "_" + name;
    }
    assert element.getKind() == ElementKind.PACKAGE;
    final String packageName = processingEnv.getElementUtils().getPackageOf(element).getQualifiedName().toString();
    return ClassName.create(packageName, "Bullet_" + name);
  }

  // This method has been copied from Dagger 2
  // Copyright (C) 2014 Google, Inc.
  private boolean isComponentProvisionMethod(ExecutableElement method) {
    return method.getParameters().isEmpty()
        && !method.getReturnType().getKind().equals(TypeKind.VOID)
        && !processingEnv.getElementUtils().getTypeElement(Object.class.getCanonicalName())
            .equals(method.getEnclosingElement());
  }

  // This method has been copied from Dagger 2
  // Copyright (C) 2014 Google, Inc.
  private boolean isComponentMembersInjectionMethod(ExecutableElement method) {
    List<? extends VariableElement> parameters = method.getParameters();
    TypeMirror returnType = method.getReturnType();
    return parameters.size() == 1
        && (returnType.getKind().equals(TypeKind.VOID)
            || MoreTypes.equivalence().equivalent(returnType, parameters.get(0).asType()))
        && !processingEnv.getElementUtils().getTypeElement(Object.class.getCanonicalName())
            .equals(method.getEnclosingElement());
  }
}
