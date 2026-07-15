package com.securechat.features.notifications;

import android.content.Context;
import com.securechat.core.network.ChatApiService;
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
public final class OneSignalHandler_Factory implements Factory<OneSignalHandler> {
  private final Provider<Context> contextProvider;

  private final Provider<ChatApiService> supabaseServiceProvider;

  public OneSignalHandler_Factory(Provider<Context> contextProvider,
      Provider<ChatApiService> supabaseServiceProvider) {
    this.contextProvider = contextProvider;
    this.supabaseServiceProvider = supabaseServiceProvider;
  }

  @Override
  public OneSignalHandler get() {
    return newInstance(contextProvider.get(), supabaseServiceProvider.get());
  }

  public static OneSignalHandler_Factory create(Provider<Context> contextProvider,
      Provider<ChatApiService> supabaseServiceProvider) {
    return new OneSignalHandler_Factory(contextProvider, supabaseServiceProvider);
  }

  public static OneSignalHandler newInstance(Context context, ChatApiService supabaseService) {
    return new OneSignalHandler(context, supabaseService);
  }
}
