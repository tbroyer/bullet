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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

import com.google.auto.common.MoreElements;
import com.google.auto.common.SuperficialValidation;
import com.google.auto.service.AutoService;
import com.google.common.base.Predicate;
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
    List<ExecutableElement> methods = ElementFilter
        .methodsIn(processingEnv.getElementUtils().getAllMembers(element));
    Iterables.removeIf(methods, new Predicate<ExecutableElement>() {
      @Override
      public boolean apply(ExecutableElement executableElement) {
        return !isComponentProvisionMethod(executableElement);
      }

      boolean isComponentProvisionMethod(ExecutableElement method) {
        return method.getParameters().isEmpty()
            && !method.getReturnType().getKind().equals(TypeKind.VOID)
            && !processingEnv.getElementUtils().getTypeElement(Object.class.getCanonicalName())
                .equals(method.getEnclosingElement());
      }
    });
    Collections.sort(methods, new Comparator<ExecutableElement>() {
      @Override
      public int compare(ExecutableElement o1, ExecutableElement o2) {
        TypeMirror r1 = o1.getReturnType(), r2 = o2.getReturnType();
        if (processingEnv.getTypeUtils().isSubtype(r1, r2)) {
          return -1;
        } else if (processingEnv.getTypeUtils().isSubtype(r2, r1)) {
          return -1;
        }
        return getName(r1).compareTo(getName(r2));
      }

      private String getName(TypeMirror type) {
        return MoreElements.asType(processingEnv.getTypeUtils().asElement(type)).getQualifiedName().toString();
      }
    });

    String name = buildName(element);
    try {
      final String packageName = processingEnv.getElementUtils().getPackageOf(element).getQualifiedName().toString();
      JavaFileObject javaFileObject = processingEnv.getFiler().createSourceFile(name, element);
      try (PrintWriter writer = new PrintWriter(javaFileObject.openWriter())) {
        writer.printf( "package %s;\n", packageName);
        writer.println();
        writer.printf("public final class %s implements %s {\n", name, ObjectGraph.class.getCanonicalName());
        writer.printf("  private final %s component;\n", element.getQualifiedName().toString());
        writer.println();
        writer.printf("  public %s(%s component) {\n", name, element.getQualifiedName().toString());
        writer.println("   this.component = component;");
        writer.println("  }");
        writer.println();
        writer.println("  @Override");
        writer.println("  public <T> T get(Class<T> type) {");
        writer.print(  "    ");
        for (ExecutableElement method : methods) {
          writer.printf("if (type == %s.class) {\n", MoreElements.asType(processingEnv.getTypeUtils().asElement(method.getReturnType())).getQualifiedName().toString());
          writer.printf("      return type.cast(this.component.%s());\n", method.getSimpleName());
          writer.print("    } else ");
        }
        writer.println("{");
        // TODO: exception message
        writer.println("      throw new IllegalArgumentException();");
        writer.println("    }");
        writer.println("  }");
        writer.println();
        writer.println("  @Override");
        writer.println("  public <T> T inject(T instance) {");
        writer.println("    throw new UnsupportedOperationException();");
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
  static boolean isComponentProvisionMethod(Elements elements, ExecutableElement method) {
    return method.getParameters().isEmpty()
        && !method.getReturnType().getKind().equals(TypeKind.VOID)
        && !elements.getTypeElement(Object.class.getCanonicalName())
        .equals(method.getEnclosingElement());
  }
}
