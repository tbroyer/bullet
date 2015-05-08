package bullet.impl;

import java.util.Collections;

import javax.annotation.processing.Processor;
import javax.lang.model.SourceVersion;

import com.google.auto.common.BasicAnnotationProcessor;
import com.google.auto.service.AutoService;

@AutoService(Processor.class)
public class ComponentProcessor extends BasicAnnotationProcessor {

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  @Override
  protected Iterable<? extends ProcessingStep> initSteps() {
    return Collections.singleton(new ComponentProcessingStep(processingEnv));
  }
}
