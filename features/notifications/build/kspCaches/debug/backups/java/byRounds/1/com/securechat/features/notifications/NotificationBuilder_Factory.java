package com.securechat.features.notifications;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
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
public final class NotificationBuilder_Factory implements Factory<NotificationBuilder> {
  private final Provider<Context> contextProvider;

  public NotificationBuilder_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public NotificationBuilder get() {
    return newInstance(contextProvider.get());
  }

  public static NotificationBuilder_Factory create(Provider<Context> contextProvider) {
    return new NotificationBuilder_Factory(contextProvider);
  }

  public static NotificationBuilder newInstance(Context context) {
    return new NotificationBuilder(context);
  }
}
