package test;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.*;

import javax.inject.Inject;
import javax.inject.Provider;

import org.junit.Before;
import org.junit.Test;
import org.mockito.internal.stubbing.defaultanswers.ForwardsInvocations;

import bullet.ObjectGraph;
import dagger.Component;
import dagger.Module;
import dagger.Provides;

public class MembersInjectionTest {

  // XXX: Dagger‡ doesn't support @Inject on abstract methods, so we cannot make I and I2 interfaces
  static abstract class I {
    E e;
    @Inject void setE(E e) { this.e = e; }
  }
  static abstract class I2 extends I {
    C c;
    @Inject void setC(C c) { this.c = c; }
  }
  static final class A extends I {
    @Inject A() {}
  }
  static class B {
    @Inject A a;
  }
  static final class C extends B {
    @Inject Provider<I> iProvider;
    @Inject C() {}
  }
  static final class D extends I2 {
  }
  static final class E {
    @Inject E() {}
  }

  // XXX: interface must be public for Mockito ForwardsInvocations to work
  @Component(modules = SimpleModule.class)
  public interface SimpleComponent {
    void i(I i);
    void i2(I2 i1);
    void a(A a);
    void b(B b);
    void c(C c);
    void d(D d);
  }

  @Module
  static class SimpleModule {
    @Provides I provideI(A a) { return a; }
  }

  SimpleComponent component;
  ObjectGraph graph;

  @Before public void setUp() {
    // We cannot spy the Dagger‡ component as it's final, so we wrap it in a mock that delegates to it.
    // We want to test both that the method is called (mockito) and that everything actually works (dagger).
    SimpleComponent realComponent = Dagger_MembersInjectionTest_SimpleComponent.create();
    this.component = mock(SimpleComponent.class, new ForwardsInvocations(realComponent));
    graph = new Bullet_MembersInjectionTest_SimpleComponent(component);
  }

  @Test public void testAnyI() {
    I i = new I() {};
    assertThat(graph.inject(i)).isSameAs(i);
    verify(component).i(i);
    assertThat(i.e).isNotNull();
    verifyNoMoreInteractions(component);
  }

  @Test public void testAny2() {
    I2 i2 = new I2() {};
    assertThat(graph.inject(i2)).isSameAs(i2);
    verify(component).i2(i2);
    assertThat(i2.c).isNotNull();
    assertThat(i2.e).isNotNull();
    verifyNoMoreInteractions(component);
  }

  @Test public void testA() {
    A a = new A();
    assertThat(graph.inject(a)).isSameAs(a);
    verify(component).a(a);
    assertThat(a.e).isNotNull();
    verifyNoMoreInteractions(component);
  }

  @Test public void testB() {
    B b = new B();
    assertThat(graph.inject(b)).isSameAs(b);
    verify(component).b(b);
    assertThat(b.a).isNotNull();
    verifyNoMoreInteractions(component);
  }

  @Test public void testC() {
    C c = new C();
    assertThat(graph.inject(c)).isSameAs(c);
    verify(component).c(c);
    assertThat(c.a).isNotNull();
    assertThat(c.iProvider).isNotNull();
    verifyNoMoreInteractions(component);
  }

  @Test public void testD() {
    D d = new D();
    assertThat(graph.inject(d)).isSameAs(d);
    verify(component).d(d);
    assertThat(d.c).isNotNull();
    assertThat(d.e).isNotNull();
    verifyNoMoreInteractions(component);
  }
}
