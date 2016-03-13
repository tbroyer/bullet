package bullet.impl;

import static com.google.common.truth.Truth.*;
import static java.util.Arrays.*;
import static java.util.Collections.*;

import java.beans.Introspector;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.google.common.base.Functions;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import com.google.testing.compile.CompilationRule;

import bullet.impl.ComponentMethodDescriptor.ComponentMethodKind;

@RunWith(Parameterized.class)
public class MembersInjectionMethodsBuilderTest {
  interface I {}
  interface I2 extends I {}
  static class A implements I {}
  static class B implements I2 {}
  static class C {}
  static class D extends B {}
  static class E extends B {}
  static class F extends D {}

  private interface Asserter {
    void check(List<Class<?>> sorted);
  }

  @Parameters(name = "{index}: {0}: {1}")
  public static Iterable<Object[]> data() {
    ArrayList<Object[]> data = new ArrayList<>();
    data.add(new Object[]{
        "single value",
        singletonList(A.class),
        new Asserter() {
          @Override
          public void check(List<Class<?>> sorted) {
            assertThat(sorted).containsExactly(A.class);
          }
        }
    });
    data.add(new Object[]{
        "single duplicate value",
        asList(A.class, A.class),
        new Asserter() {
          @Override
          public void check(List<Class<?>> sorted) {
            assertThat(sorted).containsExactly(A.class);
          }
        }
    });

    addPermutations(data, "unrelated types", asList(A.class, B.class, C.class), new Asserter() {
      @Override
      public void check(List<Class<?>> sorted) {
        assertThat(sorted).containsExactly(A.class, B.class, C.class);
      }
    });

    addPermutations(data, "simple hierarchy", asList(I.class, I2.class, B.class, D.class, F.class), new Asserter() {
      @Override
      public void check(List<Class<?>> sorted) {
        assertThat(sorted).containsExactly(F.class, D.class, B.class, I2.class, I.class).inOrder();
      }
    });

    addPermutations(data, "shared interface", asList(A.class, B.class, I.class, I2.class), new Asserter() {
      @Override
      public void check(List<Class<?>> sorted) {
        assertThat(sorted).containsExactly(A.class, B.class, I.class, I2.class);
        assertThat(sorted).containsAllOf(A.class, I.class).inOrder();
        assertThat(sorted).containsAllOf(B.class, I2.class, I.class).inOrder();
      }
    });

    addPermutations(data, "shared interface incomplete hierarchy", asList(A.class, B.class, I.class), new Asserter() {
      @Override
      public void check(List<Class<?>> sorted) {
        assertThat(sorted).containsExactly(A.class, B.class, I.class);
        assertThat(sorted).containsAllOf(A.class, I.class).inOrder();
        assertThat(sorted).containsAllOf(B.class, I.class).inOrder();
      }
    });

    addPermutations(data, "shared superclass", asList(B.class, D.class, E.class), new Asserter() {
      @Override
      public void check(List<Class<?>> sorted) {
        assertThat(sorted).containsExactly(B.class, D.class, E.class);
        assertThat(sorted).containsAllOf(D.class, B.class).inOrder();
        assertThat(sorted).containsAllOf(E.class, B.class).inOrder();
      }
    });

    addPermutations(data, "shared hierarchy mixed with unrelated class",
        asList(I.class, I2.class, A.class, B.class, C.class, D.class),
        new Asserter() {
          @Override
          public void check(List<Class<?>> sorted) {
            assertThat(sorted).containsExactly(I.class, I2.class, A.class, B.class, C.class, D.class);
            assertThat(sorted).containsAllOf(A.class, I.class).inOrder();
            assertThat(sorted).containsAllOf(D.class, B.class, I2.class, I.class).inOrder();
          }
        });
    // Ignore: generates 40K tests, taking (literally) minutes to run.
//    addPermutations(data, "all test classes",
//        asList(I.class, I2.class, A.class, B.class, C.class, D.class, E.class, F.class),
//        new Asserter() {
//          @Override
//          public void check(List<Class<?>> sorted) {
//            assertThat(sorted).containsExactly(I.class, I2.class, A.class, B.class, C.class, D.class, E.class, F.class);
//            assertThat(sorted).containsAllOf(A.class, I.class).inOrder();
//            assertThat(sorted).containsAllOf(F.class, D.class, B.class, I2.class, I.class).inOrder();
//            assertThat(sorted).containsAllOf(E.class, B.class, I2.class, I.class).inOrder();
//          }
//        });

    return data;
  }

  private static void addPermutations(ArrayList<Object[]> data, String desc, List<? extends Class<?>> inputs, Asserter asserter) {
    for (List<? extends Class<?>> permutation : Collections2.permutations(inputs)) {
      data.add(new Object[] { desc, permutation, asserter });
    }
  }

  @Rule public CompilationRule compilationRule = new CompilationRule();

  private final List<Class<?>> inputs;
  private final Asserter asserter;

  public MembersInjectionMethodsBuilderTest(@SuppressWarnings("unused") String desc, List<Class<?>> inputs, Asserter asserter) {
    this.inputs = inputs;
    this.asserter = asserter;
  }

  @Test public void test() {
    MembersInjectionMethodsBuilder sut = new MembersInjectionMethodsBuilder(compilationRule.getTypes());
    IdentityHashMap<ComponentMethodDescriptor, Class<?>> methodToClass = new IdentityHashMap<>(inputs.size());
    for (Class<?> clazz : inputs) {
      ComponentMethodDescriptor method = createComponentMethodDescriptor(clazz);
      methodToClass.put(method, clazz);
      sut.add(method);
    }

    List<ComponentMethodDescriptor> sorted = sut.build();

    List<Class<?>> sortedClasses = Lists.transform(sorted, Functions.forMap(methodToClass));
    assertThat(sortedClasses).containsNoDuplicates();

    asserter.check(sortedClasses);
  }

  private ComponentMethodDescriptor createComponentMethodDescriptor(Class<?> clazz) {
    return new AutoValue_ComponentMethodDescriptor(
        ComponentMethodKind.SIMPLE_MEMBERS_INJECTION,
        compilationRule.getTypes().getDeclaredType(
            compilationRule.getElements().getTypeElement(clazz.getCanonicalName())),
        Introspector.decapitalize(clazz.getSimpleName()));
  }
}
