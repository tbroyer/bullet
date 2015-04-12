package bullet.impl;

import java.util.Collections;

import javax.annotation.processing.Processor;

import com.google.auto.common.BasicAnnotationProcessor;
import com.google.auto.service.AutoService;

@AutoService(Processor.class)
public class ComponentProcessor extends BasicAnnotationProcessor {
  @Override
  protected Iterable<? extends ProcessingStep> initSteps() {
    return Collections.singleton(new ComponentProcessingStep(processingEnv));
  }
}
