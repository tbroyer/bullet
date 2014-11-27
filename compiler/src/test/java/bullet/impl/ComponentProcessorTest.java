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
    JavaFileObject generatedBullet = JavaFileObjects.forSourceLines("test.Bullet_SimpleComponent",
        "package test;",
        "",
        "public final class Bullet_SimpleComponent implements bullet.ObjectGraph {",
        "  private final test.SimpleComponent component;",
        "",
        "  public Bullet_SimpleComponent(test.SimpleComponent component) {",
        "    this.component = component;",
        "  }",
        "",
        "  @Override",
        "  public <T> T get(Class<T> type) {",
        "    if (type == test.SomeInjectableType.class) {",
        "      return type.cast(this.component.someInjectableType());",
        "    } else {",
        "      throw new IllegalArgumentException()",
        "    }",
        "  }",
        "",
        "  @Override",
        "  public <T> T inject(T instance) {",
        "    throw new UnsupportedOperationException();",
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
            "  final static class SomeInjectableType {",
            "    @Inject SomeInjectableType() {}",
            "  }",
            "",
            "  @Component",
            "  interface SimpleComponent {",
            "    SomeInjectableType someInjectableType();",
            "  }",
            "}");
        JavaFileObject generatedBullet = JavaFileObjects.forSourceLines("test.Bullet_OuterType_SimpleComponent",
            "package test;",
            "",
            "public final class Bullet_OuterType_SimpleComponent implements bullet.ObjectGraph {",
            "  private final test.OuterType.SimpleComponent component;",
            "",
            "  public Bullet_OuterType_SimpleComponent(test.OuterType.SimpleComponent component) {",
            "    this.component = component;",
            "  }",
            "",
            "  @Override",
            "  public <T> T get(Class<T> type) {",
            "    if (type == test.OuterType.SomeInjectableType.class) {",
            "      return type.cast(this.component.someInjectableType());",
            "    } else {",
            "      throw new IllegalArgumentException()",
            "    }",
            "  }",
            "",
            "  @Override",
            "  public <T> T inject(T instance) {",
            "    throw new UnsupportedOperationException();",
            "  }",
            "}");
        assert_().about(javaSources()).that(ImmutableList.of(nestedTypesFile))
            .processedWith(new ComponentProcessor())
            .compilesWithoutError()
            .and().generatesSources(generatedBullet);
    }
}
