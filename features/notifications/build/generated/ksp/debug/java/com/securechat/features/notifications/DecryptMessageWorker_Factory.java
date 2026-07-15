package com.securechat.features.notifications;

import android.content.Context;
import androidx.work.WorkerParameters;
import com.securechat.core.security.KeystoreManager;
import com.securechat.core.security.SessionCipherManager;
import dagger.internal.DaggerGenerated;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
@QualifierMetadata
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
public final class DecryptMessageWorker_Factory {
  private final Provider<KeystoreManager> keystoreManagerProvider;

  private final Provider<SessionCipherManager> cipherManagerProvider;

  private final Provider<NotificationBuilder> notificationBuilderProvider;

  public DecryptMessageWorker_Factory(Provider<KeystoreManager> keystoreManagerProvider,
      Provider<SessionCipherManager> cipherManagerProvider,
      Provider<NotificationBuilder> notificationBuilderProvider) {
    this.keystoreManagerProvider = keystoreManagerProvider;
    this.cipherManagerProvider = cipherManagerProvider;
    this.notificationBuilderProvider = notificationBuilderProvider;
  }

  public DecryptMessageWorker get(Context context, WorkerParameters params) {
    return newInstance(context, params, keystoreManagerProvider.get(), cipherManagerProvider.get(), notificationBuilderProvider.get());
  }

  public static DecryptMessageWorker_Factory create(
      Provider<KeystoreManager> keystoreManagerProvider,
      Provider<SessionCipherManager> cipherManagerProvider,
      Provider<NotificationBuilder> notificationBuilderProvider) {
    return new DecryptMessageWorker_Factory(keystoreManagerProvider, cipherManagerProvider, notificationBuilderProvider);
  }

  public static DecryptMessageWorker newInstance(Context context, WorkerParameters params,
      KeystoreManager keystoreManager, SessionCipherManager cipherManager,
      NotificationBuilder notificationBuilder) {
    return new DecryptMessageWorker(context, params, keystoreManager, cipherManager, notificationBuilder);
  }
}
