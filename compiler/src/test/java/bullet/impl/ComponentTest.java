package bullet.impl;

import java.lang.annotation.Annotation;

import dagger.Component;

public class ComponentTest extends AbstractComponentProcessorTest {
  @Override
  protected Class<? extends Annotation> getComponentType() {
    return Component.class;
  }
}
