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
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.auto.common.SuperficialValidation;
import com.google.auto.service.AutoService;
import com.google.common.collect.Iterables;

import bullet.ObjectGraph;
import dagger.Component;

@AutoService(Processor.class)
public class ComponentProcessor extends AbstractProcessor {

  @Override
  public Set<String> getSupportedAnnotationTypes() {
    return Collections.singleton(Component.class.getName());
  }

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    Set<? extends Element> componentElements = roundEnv.getElementsAnnotatedWith(Component.class);

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

    String name = buildName(element);
    try {
      final String packageName = processingEnv.getElementUtils().getPackageOf(element).getQualifiedName().toString();
      JavaFileObject javaFileObject = processingEnv.getFiler().createSourceFile(name, element);
      try (PrintWriter writer = new PrintWriter(javaFileObject.openWriter())) {
        writer.printf("package %s;\n", packageName);
        writer.println();
        writer.printf("@%s(\"%s\")\n", Generated.class.getCanonicalName(), ComponentProcessor.class.getCanonicalName());
        writer.printf("public final class %s implements %s {\n", name, ObjectGraph.class.getCanonicalName());
        writer.printf("  private final %s component;\n", element.getQualifiedName().toString());
        writer.println();
        writer.printf("  public %s(%s component) {\n", name, element.getQualifiedName().toString());
        writer.println("   this.component = component;");
        writer.println("  }");
        writer.println();
        writer.println("  @Override");
        writer.println("  public <T> T get(Class<T> type) {");
        for (ExecutableElement method : provisionMethods) {
          writer.printf("    if (type == %s.class) {\n", MoreElements.asType(processingEnv.getTypeUtils().asElement(method.getReturnType())).getQualifiedName().toString());
          writer.printf("      return type.cast(this.component.%s());\n", method.getSimpleName());
          writer.println("    }");
        }
        // TODO: exception message
        writer.println("    throw new IllegalArgumentException();");
        writer.println("  }");
        writer.println();
        writer.println("  @Override");
        writer.println("  public <T> T inject(T instance) {");
        for (ExecutableElement method : membersInjectionMethods) {
          String typeName = MoreElements.asType(processingEnv.getTypeUtils().asElement(Iterables.getOnlyElement(method.getParameters()).asType())).getQualifiedName().toString();
          writer.printf("    if (instance instanceof %s) {\n", typeName);
          writer.printf("      this.component.%s((%s) instance);\n", method.getSimpleName(), typeName);
          writer.println("      return instance;");
          writer.println("    }");
        }
        // TODO: exception message
        writer.println("    throw new IllegalArgumentException();");
        writer.println("  }");
        writer.println("}");
      }
    } catch (IOException ioe) {
      StringWriter sw = new StringWriter();
      PrintWriter pw = new PrintWriter(sw);
      pw.println("Error generating source file for type " + name);
      ioe.printStackTrace(pw);
      pw.close();
      processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, sw.toString());
    }
  }

  private String buildName(Element element) {
    String name = element.getSimpleName().toString();
    for (element = element.getEnclosingElement(); element.getKind() != ElementKind.PACKAGE; element = element.getEnclosingElement()) {
      name = element.getSimpleName() + "_" + name;
    }
    assert element.getKind() == ElementKind.PACKAGE;
    return "Bullet_" + name;
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
