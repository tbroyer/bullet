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

import static javax.lang.model.element.Modifier.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.TypeVariable;
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
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.auto.common.SuperficialValidation;
import com.google.auto.service.AutoService;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaPoet;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.Types;

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
      final javax.lang.model.util.Types typeUtils = processingEnv.getTypeUtils();

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

    final ClassName elementName = ClassName.get(element);

// TODO: pending https://github.com/square/javapoet/pull/181
//    TypeSpec.classBuilder("Bullet_" + Joiner.on("_").join(elementName.simpleNames()))
    final TypeSpec.Builder classBuilder = TypeSpec.classBuilder(buildBulletSimpleName(elementName))
        .addOriginatingElement(element)
        .addAnnotation(AnnotationSpec.builder(Generated.class)
            .addMember("value", "$S", getClass().getCanonicalName())
            .build())
        .addModifiers(PUBLIC, FINAL)
        .addSuperinterface(ClassName.get("bullet", "ObjectGraph"))

        .addField(elementName, "component", PRIVATE, FINAL)

        .addMethod(MethodSpec.constructorBuilder()
            .addModifiers(PUBLIC)
            .addParameter(elementName, "component", FINAL)
            .addCode("this.component = component;\n")
            .build());

    final TypeVariable<?> t = Types.typeVariable("T");
    final MethodSpec.Builder getBuilder = MethodSpec.methodBuilder("get")
        .addAnnotation(Override.class)
        .addModifiers(PUBLIC)
        .addTypeVariable(t)
        .returns(t)
        .addParameter(Types.parameterizedType(Class.class, t), "type", FINAL);
    for (ExecutableElement method : provisionMethods) {
      getBuilder.addCode(
          "if (type == $T.class) {\n$>" +
              "return type.cast(this.component.$N());\n" +
              "$<}\n",
          method.getReturnType(), method.getSimpleName());
    }
    // TODO: exception message
    getBuilder.addCode("throw new $T();\n", IllegalArgumentException.class);
    classBuilder.addMethod(getBuilder.build());

    final MethodSpec.Builder injectWriter = MethodSpec.methodBuilder("inject")
        .addAnnotation(Override.class)
        .addModifiers(PUBLIC)
        .addTypeVariable(t)
        .returns(t)
        .addParameter(t, "instance", FINAL);
    for (ExecutableElement method : membersInjectionMethods) {
      TypeMirror type = Iterables.getOnlyElement(method.getParameters()).asType();
      injectWriter.addCode(
          "if (instance instanceof $T) {\n$>" +
          "this.component.$N(($T) instance);\n" +
          "return instance;\n" +
          "$<}\n",
          type, method.getSimpleName(), type);
    }
    // TODO: exception message
    injectWriter.addCode("throw new $T();\n", IllegalArgumentException.class);
    classBuilder.addMethod(injectWriter.build());

    try {
      new JavaPoet()
          .add(elementName.packageName(), classBuilder.build())
          .writeTo(processingEnv.getFiler());
    } catch (IOException ioe) {
      StringWriter sw = new StringWriter();
      PrintWriter pw = new PrintWriter(sw);
      pw.println("Error generating source file for type " + classBuilder.build().name);
      ioe.printStackTrace(pw);
      pw.close();
      processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, sw.toString());
    }
  }

  private String buildBulletSimpleName(ClassName elementName) {
    List<CharSequence> name = new ArrayList<>();
    name.add(elementName.simpleName());
    for (elementName = elementName.enclosingClassName(); elementName != null; elementName = elementName.enclosingClassName()) {
      name.add(elementName.simpleName());
    }
    name.add("Bullet");
    Collections.reverse(name);
    return Joiner.on("_").join(name);
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
