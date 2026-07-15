package com.securechat.core.database.dao;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.securechat.core.database.entity.MessageEntity;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Integer;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class MessageDao_Impl implements MessageDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<MessageEntity> __insertionAdapterOfMessageEntity;

  private final SharedSQLiteStatement __preparedStmtOfUpdateDeliveryStatus;

  private final SharedSQLiteStatement __preparedStmtOfSoftDelete;

  private final SharedSQLiteStatement __preparedStmtOfUpdateReactions;

  private final SharedSQLiteStatement __preparedStmtOfUpdateLocalMediaPath;

  private final SharedSQLiteStatement __preparedStmtOfDeleteConversationMessages;

  public MessageDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfMessageEntity = new EntityInsertionAdapter<MessageEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `messages` (`id`,`conversation_id`,`sender_id`,`body`,`type`,`media_url`,`local_media_path`,`media_thumbnail`,`reply_to_id`,`reply_to_body`,`reactions_json`,`delivery_status`,`timestamp`,`is_mine`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final MessageEntity entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getConversationId());
        statement.bindString(3, entity.getSenderId());
        statement.bindString(4, entity.getBody());
        statement.bindString(5, entity.getType());
        if (entity.getMediaUrl() == null) {
          statement.bindNull(6);
        } else {
          statement.bindString(6, entity.getMediaUrl());
        }
        if (entity.getLocalMediaPath() == null) {
          statement.bindNull(7);
        } else {
          statement.bindString(7, entity.getLocalMediaPath());
        }
        if (entity.getMediaThumbnail() == null) {
          statement.bindNull(8);
        } else {
          statement.bindString(8, entity.getMediaThumbnail());
        }
        if (entity.getReplyToMessageId() == null) {
          statement.bindNull(9);
        } else {
          statement.bindString(9, entity.getReplyToMessageId());
        }
        if (entity.getReplyToBody() == null) {
          statement.bindNull(10);
        } else {
          statement.bindString(10, entity.getReplyToBody());
        }
        statement.bindString(11, entity.getReactionsJson());
        statement.bindString(12, entity.getDeliveryStatus());
        statement.bindLong(13, entity.getTimestamp());
        final int _tmp = entity.isMine() ? 1 : 0;
        statement.bindLong(14, _tmp);
      }
    };
    this.__preparedStmtOfUpdateDeliveryStatus = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE messages SET delivery_status = ? WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfSoftDelete = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "\n"
                + "        UPDATE messages\n"
                + "        SET type = 'DELETED', body = '', media_url = NULL, local_media_path = NULL\n"
                + "        WHERE id = ?\n"
                + "    ";
        return _query;
      }
    };
    this.__preparedStmtOfUpdateReactions = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE messages SET reactions_json = ? WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfUpdateLocalMediaPath = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE messages SET local_media_path = ? WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteConversationMessages = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM messages WHERE conversation_id = ?";
        return _query;
      }
    };
  }

  @Override
  public Object upsert(final MessageEntity message, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfMessageEntity.insert(message);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object upsertAll(final List<MessageEntity> messages,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfMessageEntity.insert(messages);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object updateDeliveryStatus(final String messageId, final String status,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateDeliveryStatus.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, status);
        _argIndex = 2;
        _stmt.bindString(_argIndex, messageId);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfUpdateDeliveryStatus.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object softDelete(final String messageId, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfSoftDelete.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, messageId);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfSoftDelete.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object updateReactions(final String messageId, final String reactionsJson,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateReactions.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, reactionsJson);
        _argIndex = 2;
        _stmt.bindString(_argIndex, messageId);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfUpdateReactions.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object updateLocalMediaPath(final String messageId, final String localPath,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateLocalMediaPath.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, localPath);
        _argIndex = 2;
        _stmt.bindString(_argIndex, messageId);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfUpdateLocalMediaPath.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteConversationMessages(final String conversationId,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteConversationMessages.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, conversationId);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteConversationMessages.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<MessageEntity>> observeMessages(final String conversationId) {
    final String _sql = "\n"
            + "        SELECT * FROM messages\n"
            + "        WHERE conversation_id = ?\n"
            + "        ORDER BY timestamp ASC\n"
            + "    ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, conversationId);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"messages"}, new Callable<List<MessageEntity>>() {
      @Override
      @NonNull
      public List<MessageEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfConversationId = CursorUtil.getColumnIndexOrThrow(_cursor, "conversation_id");
          final int _cursorIndexOfSenderId = CursorUtil.getColumnIndexOrThrow(_cursor, "sender_id");
          final int _cursorIndexOfBody = CursorUtil.getColumnIndexOrThrow(_cursor, "body");
          final int _cursorIndexOfType = CursorUtil.getColumnIndexOrThrow(_cursor, "type");
          final int _cursorIndexOfMediaUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "media_url");
          final int _cursorIndexOfLocalMediaPath = CursorUtil.getColumnIndexOrThrow(_cursor, "local_media_path");
          final int _cursorIndexOfMediaThumbnail = CursorUtil.getColumnIndexOrThrow(_cursor, "media_thumbnail");
          final int _cursorIndexOfReplyToMessageId = CursorUtil.getColumnIndexOrThrow(_cursor, "reply_to_id");
          final int _cursorIndexOfReplyToBody = CursorUtil.getColumnIndexOrThrow(_cursor, "reply_to_body");
          final int _cursorIndexOfReactionsJson = CursorUtil.getColumnIndexOrThrow(_cursor, "reactions_json");
          final int _cursorIndexOfDeliveryStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "delivery_status");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfIsMine = CursorUtil.getColumnIndexOrThrow(_cursor, "is_mine");
          final List<MessageEntity> _result = new ArrayList<MessageEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final MessageEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpConversationId;
            _tmpConversationId = _cursor.getString(_cursorIndexOfConversationId);
            final String _tmpSenderId;
            _tmpSenderId = _cursor.getString(_cursorIndexOfSenderId);
            final String _tmpBody;
            _tmpBody = _cursor.getString(_cursorIndexOfBody);
            final String _tmpType;
            _tmpType = _cursor.getString(_cursorIndexOfType);
            final String _tmpMediaUrl;
            if (_cursor.isNull(_cursorIndexOfMediaUrl)) {
              _tmpMediaUrl = null;
            } else {
              _tmpMediaUrl = _cursor.getString(_cursorIndexOfMediaUrl);
            }
            final String _tmpLocalMediaPath;
            if (_cursor.isNull(_cursorIndexOfLocalMediaPath)) {
              _tmpLocalMediaPath = null;
            } else {
              _tmpLocalMediaPath = _cursor.getString(_cursorIndexOfLocalMediaPath);
            }
            final String _tmpMediaThumbnail;
            if (_cursor.isNull(_cursorIndexOfMediaThumbnail)) {
              _tmpMediaThumbnail = null;
            } else {
              _tmpMediaThumbnail = _cursor.getString(_cursorIndexOfMediaThumbnail);
            }
            final String _tmpReplyToMessageId;
            if (_cursor.isNull(_cursorIndexOfReplyToMessageId)) {
              _tmpReplyToMessageId = null;
            } else {
              _tmpReplyToMessageId = _cursor.getString(_cursorIndexOfReplyToMessageId);
            }
            final String _tmpReplyToBody;
            if (_cursor.isNull(_cursorIndexOfReplyToBody)) {
              _tmpReplyToBody = null;
            } else {
              _tmpReplyToBody = _cursor.getString(_cursorIndexOfReplyToBody);
            }
            final String _tmpReactionsJson;
            _tmpReactionsJson = _cursor.getString(_cursorIndexOfReactionsJson);
            final String _tmpDeliveryStatus;
            _tmpDeliveryStatus = _cursor.getString(_cursorIndexOfDeliveryStatus);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final boolean _tmpIsMine;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsMine);
            _tmpIsMine = _tmp != 0;
            _item = new MessageEntity(_tmpId,_tmpConversationId,_tmpSenderId,_tmpBody,_tmpType,_tmpMediaUrl,_tmpLocalMediaPath,_tmpMediaThumbnail,_tmpReplyToMessageId,_tmpReplyToBody,_tmpReactionsJson,_tmpDeliveryStatus,_tmpTimestamp,_tmpIsMine);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object getMessagesBefore(final String conversationId, final long beforeTimestamp,
      final int pageSize, final Continuation<? super List<MessageEntity>> $completion) {
    final String _sql = "\n"
            + "        SELECT * FROM messages\n"
            + "        WHERE conversation_id = ?\n"
            + "          AND timestamp < ?\n"
            + "        ORDER BY timestamp DESC\n"
            + "        LIMIT ?\n"
            + "    ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 3);
    int _argIndex = 1;
    _statement.bindString(_argIndex, conversationId);
    _argIndex = 2;
    _statement.bindLong(_argIndex, beforeTimestamp);
    _argIndex = 3;
    _statement.bindLong(_argIndex, pageSize);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<MessageEntity>>() {
      @Override
      @NonNull
      public List<MessageEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfConversationId = CursorUtil.getColumnIndexOrThrow(_cursor, "conversation_id");
          final int _cursorIndexOfSenderId = CursorUtil.getColumnIndexOrThrow(_cursor, "sender_id");
          final int _cursorIndexOfBody = CursorUtil.getColumnIndexOrThrow(_cursor, "body");
          final int _cursorIndexOfType = CursorUtil.getColumnIndexOrThrow(_cursor, "type");
          final int _cursorIndexOfMediaUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "media_url");
          final int _cursorIndexOfLocalMediaPath = CursorUtil.getColumnIndexOrThrow(_cursor, "local_media_path");
          final int _cursorIndexOfMediaThumbnail = CursorUtil.getColumnIndexOrThrow(_cursor, "media_thumbnail");
          final int _cursorIndexOfReplyToMessageId = CursorUtil.getColumnIndexOrThrow(_cursor, "reply_to_id");
          final int _cursorIndexOfReplyToBody = CursorUtil.getColumnIndexOrThrow(_cursor, "reply_to_body");
          final int _cursorIndexOfReactionsJson = CursorUtil.getColumnIndexOrThrow(_cursor, "reactions_json");
          final int _cursorIndexOfDeliveryStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "delivery_status");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfIsMine = CursorUtil.getColumnIndexOrThrow(_cursor, "is_mine");
          final List<MessageEntity> _result = new ArrayList<MessageEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final MessageEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpConversationId;
            _tmpConversationId = _cursor.getString(_cursorIndexOfConversationId);
            final String _tmpSenderId;
            _tmpSenderId = _cursor.getString(_cursorIndexOfSenderId);
            final String _tmpBody;
            _tmpBody = _cursor.getString(_cursorIndexOfBody);
            final String _tmpType;
            _tmpType = _cursor.getString(_cursorIndexOfType);
            final String _tmpMediaUrl;
            if (_cursor.isNull(_cursorIndexOfMediaUrl)) {
              _tmpMediaUrl = null;
            } else {
              _tmpMediaUrl = _cursor.getString(_cursorIndexOfMediaUrl);
            }
            final String _tmpLocalMediaPath;
            if (_cursor.isNull(_cursorIndexOfLocalMediaPath)) {
              _tmpLocalMediaPath = null;
            } else {
              _tmpLocalMediaPath = _cursor.getString(_cursorIndexOfLocalMediaPath);
            }
            final String _tmpMediaThumbnail;
            if (_cursor.isNull(_cursorIndexOfMediaThumbnail)) {
              _tmpMediaThumbnail = null;
            } else {
              _tmpMediaThumbnail = _cursor.getString(_cursorIndexOfMediaThumbnail);
            }
            final String _tmpReplyToMessageId;
            if (_cursor.isNull(_cursorIndexOfReplyToMessageId)) {
              _tmpReplyToMessageId = null;
            } else {
              _tmpReplyToMessageId = _cursor.getString(_cursorIndexOfReplyToMessageId);
            }
            final String _tmpReplyToBody;
            if (_cursor.isNull(_cursorIndexOfReplyToBody)) {
              _tmpReplyToBody = null;
            } else {
              _tmpReplyToBody = _cursor.getString(_cursorIndexOfReplyToBody);
            }
            final String _tmpReactionsJson;
            _tmpReactionsJson = _cursor.getString(_cursorIndexOfReactionsJson);
            final String _tmpDeliveryStatus;
            _tmpDeliveryStatus = _cursor.getString(_cursorIndexOfDeliveryStatus);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final boolean _tmpIsMine;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsMine);
            _tmpIsMine = _tmp != 0;
            _item = new MessageEntity(_tmpId,_tmpConversationId,_tmpSenderId,_tmpBody,_tmpType,_tmpMediaUrl,_tmpLocalMediaPath,_tmpMediaThumbnail,_tmpReplyToMessageId,_tmpReplyToBody,_tmpReactionsJson,_tmpDeliveryStatus,_tmpTimestamp,_tmpIsMine);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getById(final String messageId,
      final Continuation<? super MessageEntity> $completion) {
    final String _sql = "SELECT * FROM messages WHERE id = ? LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, messageId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<MessageEntity>() {
      @Override
      @Nullable
      public MessageEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfConversationId = CursorUtil.getColumnIndexOrThrow(_cursor, "conversation_id");
          final int _cursorIndexOfSenderId = CursorUtil.getColumnIndexOrThrow(_cursor, "sender_id");
          final int _cursorIndexOfBody = CursorUtil.getColumnIndexOrThrow(_cursor, "body");
          final int _cursorIndexOfType = CursorUtil.getColumnIndexOrThrow(_cursor, "type");
          final int _cursorIndexOfMediaUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "media_url");
          final int _cursorIndexOfLocalMediaPath = CursorUtil.getColumnIndexOrThrow(_cursor, "local_media_path");
          final int _cursorIndexOfMediaThumbnail = CursorUtil.getColumnIndexOrThrow(_cursor, "media_thumbnail");
          final int _cursorIndexOfReplyToMessageId = CursorUtil.getColumnIndexOrThrow(_cursor, "reply_to_id");
          final int _cursorIndexOfReplyToBody = CursorUtil.getColumnIndexOrThrow(_cursor, "reply_to_body");
          final int _cursorIndexOfReactionsJson = CursorUtil.getColumnIndexOrThrow(_cursor, "reactions_json");
          final int _cursorIndexOfDeliveryStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "delivery_status");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfIsMine = CursorUtil.getColumnIndexOrThrow(_cursor, "is_mine");
          final MessageEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpConversationId;
            _tmpConversationId = _cursor.getString(_cursorIndexOfConversationId);
            final String _tmpSenderId;
            _tmpSenderId = _cursor.getString(_cursorIndexOfSenderId);
            final String _tmpBody;
            _tmpBody = _cursor.getString(_cursorIndexOfBody);
            final String _tmpType;
            _tmpType = _cursor.getString(_cursorIndexOfType);
            final String _tmpMediaUrl;
            if (_cursor.isNull(_cursorIndexOfMediaUrl)) {
              _tmpMediaUrl = null;
            } else {
              _tmpMediaUrl = _cursor.getString(_cursorIndexOfMediaUrl);
            }
            final String _tmpLocalMediaPath;
            if (_cursor.isNull(_cursorIndexOfLocalMediaPath)) {
              _tmpLocalMediaPath = null;
            } else {
              _tmpLocalMediaPath = _cursor.getString(_cursorIndexOfLocalMediaPath);
            }
            final String _tmpMediaThumbnail;
            if (_cursor.isNull(_cursorIndexOfMediaThumbnail)) {
              _tmpMediaThumbnail = null;
            } else {
              _tmpMediaThumbnail = _cursor.getString(_cursorIndexOfMediaThumbnail);
            }
            final String _tmpReplyToMessageId;
            if (_cursor.isNull(_cursorIndexOfReplyToMessageId)) {
              _tmpReplyToMessageId = null;
            } else {
              _tmpReplyToMessageId = _cursor.getString(_cursorIndexOfReplyToMessageId);
            }
            final String _tmpReplyToBody;
            if (_cursor.isNull(_cursorIndexOfReplyToBody)) {
              _tmpReplyToBody = null;
            } else {
              _tmpReplyToBody = _cursor.getString(_cursorIndexOfReplyToBody);
            }
            final String _tmpReactionsJson;
            _tmpReactionsJson = _cursor.getString(_cursorIndexOfReactionsJson);
            final String _tmpDeliveryStatus;
            _tmpDeliveryStatus = _cursor.getString(_cursorIndexOfDeliveryStatus);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final boolean _tmpIsMine;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsMine);
            _tmpIsMine = _tmp != 0;
            _result = new MessageEntity(_tmpId,_tmpConversationId,_tmpSenderId,_tmpBody,_tmpType,_tmpMediaUrl,_tmpLocalMediaPath,_tmpMediaThumbnail,_tmpReplyToMessageId,_tmpReplyToBody,_tmpReactionsJson,_tmpDeliveryStatus,_tmpTimestamp,_tmpIsMine);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<Integer> observeUnreadCount(final String conversationId) {
    final String _sql = "SELECT COUNT(*) FROM messages WHERE conversation_id = ? AND is_mine = 0 AND delivery_status != 'READ'";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, conversationId);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"messages"}, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp;
          } else {
            _result = 0;
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
