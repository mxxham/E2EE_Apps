package com.securechat.features.conversations;

import com.securechat.core.database.dao.ConversationDao;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
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
public final class ConversationListViewModel_Factory implements Factory<ConversationListViewModel> {
  private final Provider<ConversationDao> conversationDaoProvider;

  public ConversationListViewModel_Factory(Provider<ConversationDao> conversationDaoProvider) {
    this.conversationDaoProvider = conversationDaoProvider;
  }

  @Override
  public ConversationListViewModel get() {
    return newInstance(conversationDaoProvider.get());
  }

  public static ConversationListViewModel_Factory create(
      Provider<ConversationDao> conversationDaoProvider) {
    return new ConversationListViewModel_Factory(conversationDaoProvider);
  }

  public static ConversationListViewModel newInstance(ConversationDao conversationDao) {
    return new ConversationListViewModel(conversationDao);
  }
}
