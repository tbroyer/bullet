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

import static com.google.common.truth.Truth.assert_;
import static com.google.testing.compile.JavaSourcesSubjectFactory.javaSources;
import com.google.common.collect.ImmutableList;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;

import org.junit.Test;

public class ComponentProcessorTest {

  @Test public void simpleComponent() {
    JavaFileObject injectableTypeFile = JavaFileObjects.forSourceLines("test.SomeInjectableType",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "final class SomeInjectableType {",
        "  @Inject SomeInjectableType() {}",
        "}");
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.SimpleComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component",
        "interface SimpleComponent {",
        "  SomeInjectableType someInjectableType();",
        "}");
    JavaFileObject generatedBullet = JavaFileObjects.forSourceLines("test.BulletSimpleComponent",
        "package test;",
        "",
        "import bullet.ObjectGraph;",
        "import java.lang.Class;",
        "import java.lang.IllegalArgumentException;",
        "import java.lang.Override;",
        "import javax.annotation.Generated;",
        "",
        "@Generated(\"bullet.impl.ComponentProcessor\")",
        "public final class BulletSimpleComponent implements ObjectGraph {",
        "  private final SimpleComponent component;",
        "",
        "  public BulletSimpleComponent(final SimpleComponent component) {",
        "    this.component = component;",
        "  }",
        "",
        "  @Override",
        "  public <T> T get(final Class<T> type) {",
        "    if (type == SomeInjectableType.class) {",
        "      return type.cast(this.component.someInjectableType());",
        "    }",
        "    throw new IllegalArgumentException()",
        "  }",
        "",
        "  @Override",
        "  public <T> T inject(final T instance) {",
        "    throw new IllegalArgumentException();",
        "  }",
        "}");
    assert_().about(javaSources()).that(ImmutableList.of(injectableTypeFile, componentFile))
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and().generatesSources(generatedBullet);
  }

  @Test public void simpleComponentWithNesting() {
    JavaFileObject nestedTypesFile = JavaFileObjects.forSourceLines("test.OuterType",
        "package test;",
        "",
        "import dagger.Component;",
        "import javax.inject.Inject;",
        "",
        "final class OuterType {",
        "  final static class A {",
        "    @Inject A() {}",
        "  }",
        "  final static class B {",
        "    @Inject A a;",
        "  }",
        "  @Component interface SimpleComponent {",
        "    A a();",
        "    void inject(B b);",
        "  }",
        "}");
    JavaFileObject generatedBullet = JavaFileObjects.forSourceLines("test.BulletOuterType_SimpleComponent",
        "package test;",
        "",
        "import bullet.ObjectGraph;",
        "import java.lang.Class;",
        "import java.lang.IllegalArgumentException;",
        "import java.lang.Override;",
        "import javax.annotation.Generated;",
        "",
        "@Generated(\"bullet.impl.ComponentProcessor\")",
        "public final class BulletOuterType_SimpleComponent implements ObjectGraph {",
        "  private final OuterType.SimpleComponent component;",
        "",
        "  public BulletOuterType_SimpleComponent(final OuterType.SimpleComponent component) {",
        "    this.component = component;",
        "  }",
        "",
        "  @Override",
        "  public <T> T get(final Class<T> type) {",
        "    if (type == OuterType.A.class) {",
        "      return type.cast(this.component.a());",
        "    }",
        "    throw new IllegalArgumentException()",
        "  }",
        "",
        "  @Override",
        "  public <T> T inject(final T instance) {",
        "    if (instance instanceof OuterType.B) {",
        "      this.component.inject((OuterType.B) instance)",
        "      return instance;",
        "    }",
        "    throw new IllegalArgumentException();",
        "  }",
        "}");
    assert_().about(javaSources()).that(ImmutableList.of(nestedTypesFile))
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and().generatesSources(generatedBullet);
  }

  @Test public void membersInjectionTypePrecedence() {
    // XXX: Dagger‡ doesn't support @Inject on abstract methods (e.g. interfaces), but we support it here anyway.
    JavaFileObject iFile = JavaFileObjects.forSourceLines("test.I",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "interface I {",
        "  @Inject void setE(E e);",
        "}");
    JavaFileObject i2File = JavaFileObjects.forSourceLines("test.I2",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "interface I2 extends I {",
        "  @Inject void setC(C c);",
        "}");
    JavaFileObject aFile = JavaFileObjects.forSourceLines("test.A",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "final class A implements I {",
        "  public void setE(E e) {};",
        "}");
    JavaFileObject bFile = JavaFileObjects.forSourceLines("test.B",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "class B {",
        "  @Inject A a;",
        "}");
    JavaFileObject cFile = JavaFileObjects.forSourceLines("test.C",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "import javax.inject.Provider;",
        "",
        "final class C extends B {",
        "  @Inject Provider<I> iProvider;",
        "}");
    JavaFileObject dFile = JavaFileObjects.forSourceLines("test.D",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "final class D implements I2 {",
        "  public void setC(C c) {}",
        "  public void setE(E e) {}",
        "}");
    JavaFileObject eFile = JavaFileObjects.forSourceLines("test.E",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "final class E {",
        "}");
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.SimpleComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component",
        "interface SimpleComponent {",
        "  void inject(I i);",
        "  void inject(I2 i1);",
        "  void inject(A a);",
        "  void inject(B b);",
        "  void inject(C c);",
        "  void inject(D d);",
        "}");
    JavaFileObject generatedBullet = JavaFileObjects.forSourceLines("test.BulletSimpleComponent",
        "package test;",
        "",
        "import bullet.ObjectGraph;",
        "import java.lang.Class;",
        "import java.lang.IllegalArgumentException;",
        "import java.lang.Override;",
        "import javax.annotation.Generated;",
        "",
        "@Generated(\"bullet.impl.ComponentProcessor\")",
        "public final class BulletSimpleComponent implements ObjectGraph {",
        "  private final SimpleComponent component;",
        "",
        "  public BulletSimpleComponent(final SimpleComponent component) {",
        "    this.component = component;",
        "  }",
        "",
        "  @Override",
        "  public <T> T get(final Class<T> type) {",
        "    throw new IllegalArgumentException()",
        "  }",
        "",
        "  @Override",
        /*
         * Note:
         *  - A before I (as A implements I)
         *  - C before B (as C extends B)
         *  - D before I2 (as D implements I2)
         *  - I2 before I (as I2 extends I)
         *  - A before B, C and D; and D after A, B and C (natural ordering of names)
         */
      "  public <T> T inject(final T instance) {",
        "    if (instance instanceof A) {",
        "      this.component.inject((A) instance);",
        "      return instance;",
        "    }",
        "    if (instance instanceof C) {",
        "      this.component.inject((C) instance);",
        "      return instance;",
        "    }",
        "    if (instance instanceof B) {",
        "      this.component.inject((B) instance);",
        "      return instance;",
        "    }",
        "    if (instance instanceof D) {",
        "      this.component.inject((D) instance);",
        "      return instance;",
        "    }",
        "    if (instance instanceof I2) {",
        "      this.component.inject((I2) instance);",
        "      return instance;",
        "    }",
        "    if (instance instanceof I) {",
        "      this.component.inject((I) instance);",
        "      return instance;",
        "    }",
        "    throw new IllegalArgumentException();",
        "  }",
        "}");
    assert_().about(javaSources()).that(ImmutableList.of(iFile, i2File, aFile, bFile, cFile, dFile, eFile, componentFile))
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and().generatesSources(generatedBullet);
  }

  @Test public void nonVisibleMethods() {
    JavaFileObject aFile = JavaFileObjects.forSourceLines("other.A",
        "package other;",
        "",
        "import javax.inject.Inject;",
        "",
        "public final class A {",
        "  @Inject A() {}",
        "}");
    JavaFileObject bFile = JavaFileObjects.forSourceLines("other.B",
        "package other;",
        "",
        "import javax.inject.Inject;",
        "",
        "public final class B {",
        "  @Inject B() {}",
        "}");
    JavaFileObject cFile = JavaFileObjects.forSourceLines("other.C",
        "package other;",
        "",
        "import javax.inject.Inject;",
        "",
        "public final class C {",
        "  @Inject C() {}",
        "}");
    JavaFileObject dFile = JavaFileObjects.forSourceLines("other.D",
        "package other;",
        "",
        "import javax.inject.Inject;",
        "",
        "final class D {",
        "  @Inject D() {}",
        "}");
    JavaFileObject superComponentInOtherPackageFile = JavaFileObjects.forSourceLines("other.SuperComponentInOtherPackage",
        "package other;",
        "",
        "public class SuperComponentInOtherPackage {",
        "  public A publicMethodInOtherPackage() { return null; }",
        "  protected B protectedMethodInOtherPackage() { return null; }",
        "  private C privateMethod() { return null; }",
        "  public D nonVisibleReturnType() { return null; }",
        "}");
    JavaFileObject eFile = JavaFileObjects.forSourceLines("test.E",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "final class E {",
        "  @Inject E() {}",
        "}");
    JavaFileObject fFile = JavaFileObjects.forSourceLines("test.F",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "final class F {",
        "  @Inject F() {}",
        "}");
    JavaFileObject superComponentFile = JavaFileObjects.forSourceLines("test.SuperComponent",
        "package test;",
        "",
        "import other.SuperComponentInOtherPackage;",
        "",
        "class SuperComponent extends SuperComponentInOtherPackage {",
        "  protected E protectedMethodInSamePackage() { return null; }",
        "  private F privateMethod() { return null; }",
        "}");
    JavaFileObject gFile = JavaFileObjects.forSourceLines("test.G",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "final class G {",
        "  @Inject G() {}",
        "}");
    JavaFileObject hFile = JavaFileObjects.forSourceLines("test.H",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "final class H {",
        "  @Inject H() {}",
        "}");
    JavaFileObject iFile = JavaFileObjects.forSourceLines("test.I",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "final class I {",
        "  @Inject I() {}",
        "}");
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.SimpleComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component",
        "class SimpleComponent extends SuperComponent {",
        "  protected G protectedMethod() { return null; }",
        "  private H privateMethod() { return null; }",
        "}");
    JavaFileObject generatedBullet = JavaFileObjects.forSourceLines("test.BulletSimpleComponent",
        "package test;",
        "",
        "import bullet.ObjectGraph;",
        "import java.lang.Class;",
        "import java.lang.IllegalArgumentException;",
        "import java.lang.Override;",
        "import javax.annotation.Generated;",
        "import other.A;",
        "",
        "@Generated(\"bullet.impl.ComponentProcessor\")",
        "public final class BulletSimpleComponent implements ObjectGraph {",
        "  private final SimpleComponent component;",
        "",
        "  public BulletSimpleComponent(final SimpleComponent component) {",
        "    this.component = component;",
        "  }",
        "",
        "  @Override",
        "  public <T> T get(final Class<T> type) {",
        "    if (type == A.class) {",
        "      return type.cast(this.component.publicMethodInOtherPackage());",
        "    }",
        "    if (type == E.class) {",
        "      return type.cast(this.component.protectedMethodInSamePackage());",
        "    }",
        "    if (type == G.class) {",
        "      return type.cast(this.component.protectedMethod());",
        "    }",
        "    throw new IllegalArgumentException()",
        "  }",
        "",
        "  @Override",
        "  public <T> T inject(final T instance) {",
        "    throw new IllegalArgumentException();",
        "  }",
        "}");
    assert_().about(javaSources()).that(ImmutableList.of(
            aFile, bFile, cFile, dFile, eFile, fFile, gFile, hFile, iFile,
            superComponentInOtherPackageFile, superComponentFile, componentFile))
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and().generatesSources(generatedBullet);
  }
}
