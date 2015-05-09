package bullet.impl;

import java.lang.annotation.Annotation;

import dagger.Subcomponent;

public class SubcomponentTest extends AbstractComponentProcessorTest {
  @Override
  protected Class<? extends Annotation> getComponentType() {
    return Subcomponent.class;
  }
}
