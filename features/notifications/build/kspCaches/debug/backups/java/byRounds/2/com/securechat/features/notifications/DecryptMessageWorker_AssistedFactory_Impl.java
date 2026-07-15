package com.securechat.features.notifications;

import android.content.Context;
import androidx.work.WorkerParameters;
import dagger.internal.DaggerGenerated;
import dagger.internal.InstanceFactory;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast"
})
public final class DecryptMessageWorker_AssistedFactory_Impl implements DecryptMessageWorker_AssistedFactory {
  private final DecryptMessageWorker_Factory delegateFactory;

  DecryptMessageWorker_AssistedFactory_Impl(DecryptMessageWorker_Factory delegateFactory) {
    this.delegateFactory = delegateFactory;
  }

  @Override
  public DecryptMessageWorker create(Context p0, WorkerParameters p1) {
    return delegateFactory.get(p0, p1);
  }

  public static Provider<DecryptMessageWorker_AssistedFactory> create(
      DecryptMessageWorker_Factory delegateFactory) {
    return InstanceFactory.create(new DecryptMessageWorker_AssistedFactory_Impl(delegateFactory));
  }

  public static dagger.internal.Provider<DecryptMessageWorker_AssistedFactory> createFactoryProvider(
      DecryptMessageWorker_Factory delegateFactory) {
    return InstanceFactory.create(new DecryptMessageWorker_AssistedFactory_Impl(delegateFactory));
  }
}
