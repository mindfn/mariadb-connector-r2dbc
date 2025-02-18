// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2020-2022 MariaDB Corporation Ab

package org.mariadb.r2dbc.codec.list;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.EnumSet;
import org.mariadb.r2dbc.ExceptionFactory;
import org.mariadb.r2dbc.codec.Codec;
import org.mariadb.r2dbc.codec.DataType;
import org.mariadb.r2dbc.message.Context;
import org.mariadb.r2dbc.message.server.ColumnDefinitionPacket;
import org.mariadb.r2dbc.util.BindValue;

public class LocalDateTimeCodec implements Codec<LocalDateTime> {

  public static final LocalDateTimeCodec INSTANCE = new LocalDateTimeCodec();
  public static final DateTimeFormatter TIMESTAMP_FORMAT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS");
  public static final DateTimeFormatter TIMESTAMP_FORMAT_NO_FRACTIONAL =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  public static final DateTimeFormatter MARIADB_LOCAL_DATE_TIME;
  private static final EnumSet<DataType> COMPATIBLE_TYPES =
      EnumSet.of(
          DataType.DATETIME,
          DataType.TIMESTAMP,
          DataType.VARSTRING,
          DataType.TEXT,
          DataType.STRING,
          DataType.TIME,
          DataType.DATE);

  static {
    MARIADB_LOCAL_DATE_TIME =
        new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .append(DateTimeFormatter.ISO_LOCAL_DATE)
            .appendLiteral(' ')
            .append(DateTimeFormatter.ISO_LOCAL_TIME)
            .toFormatter();
  }

  public static int[] parseTimestamp(String raw) {
    int nanoLen = -1;
    int[] timestampsPart = new int[] {0, 0, 0, 0, 0, 0, 0};
    int partIdx = 0;
    for (int idx = 0; idx < raw.length(); idx++) {
      char b = raw.charAt(idx);
      if (b == '-' || b == ' ' || b == ':') {
        partIdx++;
        continue;
      }
      if (b == '.') {
        partIdx++;
        nanoLen = 0;
        continue;
      }
      if (nanoLen >= 0) nanoLen++;
      timestampsPart[partIdx] = timestampsPart[partIdx] * 10 + b - 48;
    }
    if (timestampsPart[0] == 0 && timestampsPart[1] == 0 && timestampsPart[2] == 0) {
      if (timestampsPart[3] == 0
          && timestampsPart[4] == 0
          && timestampsPart[5] == 0
          && timestampsPart[6] == 0) return null;
      timestampsPart[1] = 1;
      timestampsPart[2] = 1;
    }

    // fix non leading tray for nanoseconds
    if (nanoLen >= 0) {
      for (int begin = 0; begin < 6 - nanoLen; begin++) {
        timestampsPart[6] = timestampsPart[6] * 10;
      }
      timestampsPart[6] = timestampsPart[6] * 1000;
    }
    return timestampsPart;
  }

  public boolean canDecode(ColumnDefinitionPacket column, Class<?> type) {
    return COMPATIBLE_TYPES.contains(column.getDataType())
        && type.isAssignableFrom(LocalDateTime.class);
  }

  public boolean canEncode(Class<?> value) {
    return LocalDateTime.class.isAssignableFrom(value);
  }

  @Override
  public LocalDateTime decodeText(
      ByteBuf buf,
      int length,
      ColumnDefinitionPacket column,
      Class<? extends LocalDateTime> type,
      ExceptionFactory factory) {

    int[] parts;
    switch (column.getDataType()) {
      case DATE:
        parts = LocalDateCodec.parseDate(buf, length);
        if (parts == null) return null;
        return LocalDateTime.of(parts[0], parts[1], parts[2], 0, 0, 0);

      case DATETIME:
      case TIMESTAMP:
        parts = parseTimestamp(buf.readCharSequence(length, StandardCharsets.US_ASCII).toString());
        if (parts == null) return null;
        return LocalDateTime.of(parts[0], parts[1], parts[2], parts[3], parts[4], parts[5])
            .plusNanos(parts[6]);

      case TIME:
        parts = LocalTimeCodec.parseTime(buf, length, column, factory);
        return LocalDateTime.of(1970, 1, 1, parts[1] % 24, parts[2], parts[3]).plusNanos(parts[4]);

      default:
        // STRING, VARCHAR, VARSTRING:
        String val = buf.readCharSequence(length, StandardCharsets.UTF_8).toString();
        try {
          parts = parseTimestamp(val);
          if (parts == null) return null;
          return LocalDateTime.of(parts[0], parts[1], parts[2], parts[3], parts[4], parts[5])
              .plusNanos(parts[6]);
        } catch (DateTimeException dte) {
          throw factory.createParsingException(
              String.format(
                  "value '%s' (%s) cannot be decoded as LocalDateTime", val, column.getDataType()));
        }
    }
  }

  @Override
  public LocalDateTime decodeBinary(
      ByteBuf buf,
      int length,
      ColumnDefinitionPacket column,
      Class<? extends LocalDateTime> type,
      ExceptionFactory factory) {

    int year = 1970;
    int month = 1;
    long dayOfMonth = 1;
    int hour = 0;
    int minutes = 0;
    int seconds = 0;
    long microseconds = 0;

    switch (column.getDataType()) {
      case TIME:
        // specific case for TIME, to handle value not in 00:00:00-23:59:59
        if (length > 0) {
          buf.skipBytes(5); // skip negative and days
          hour = buf.readByte();
          minutes = buf.readByte();
          seconds = buf.readByte();
          if (length > 8) {
            microseconds = buf.readUnsignedIntLE();
          }
        }
        break;

      case DATE:
      case TIMESTAMP:
      case DATETIME:
        if (length > 0) {
          year = buf.readUnsignedShortLE();
          month = buf.readByte();
          dayOfMonth = buf.readByte();

          if (length > 4) {
            hour = buf.readByte();
            minutes = buf.readByte();
            seconds = buf.readByte();

            if (length > 7) {
              microseconds = buf.readUnsignedIntLE();
            }
          }
        } else return null;
        break;

      default:
        // STRING, VARCHAR, VARSTRING:
        String val = buf.readCharSequence(length, StandardCharsets.UTF_8).toString();
        try {
          int[] parts = parseTimestamp(val);
          if (parts == null) return null;
          return LocalDateTime.of(parts[0], parts[1], parts[2], parts[3], parts[4], parts[5])
              .plusNanos(parts[6]);
        } catch (DateTimeException dte) {
          throw factory.createParsingException(
              String.format(
                  "value '%s' (%s) cannot be decoded as LocalDateTime", val, column.getDataType()));
        }
    }

    return LocalDateTime.of(year, month, (int) dayOfMonth, hour, minutes, seconds)
        .plusNanos(microseconds * 1000);
  }

  @Override
  public BindValue encodeText(
      ByteBufAllocator allocator, Object value, Context context, ExceptionFactory factory) {
    return createEncodedValue(
        () -> {
          LocalDateTime val = (LocalDateTime) value;
          ByteBuf buf = allocator.buffer();
          buf.writeByte('\'');
          buf.writeCharSequence(
              val.format(val.getNano() != 0 ? TIMESTAMP_FORMAT : TIMESTAMP_FORMAT_NO_FRACTIONAL),
              StandardCharsets.US_ASCII);
          buf.writeByte('\'');
          return buf;
        });
  }

  @Override
  public BindValue encodeBinary(
      ByteBufAllocator allocator, Object value, ExceptionFactory factory) {
    return createEncodedValue(
        () -> {
          ByteBuf buf;
          LocalDateTime val = (LocalDateTime) value;
          int nano = val.getNano();
          if (nano > 0) {
            buf = allocator.buffer(12, 12);
            buf.writeByte((byte) 11);
            buf.writeShortLE((short) val.get(ChronoField.YEAR));
            buf.writeByte(val.get(ChronoField.MONTH_OF_YEAR));
            buf.writeByte(val.get(ChronoField.DAY_OF_MONTH));
            buf.writeByte(val.get(ChronoField.HOUR_OF_DAY));
            buf.writeByte(val.get(ChronoField.MINUTE_OF_HOUR));
            buf.writeByte(val.get(ChronoField.SECOND_OF_MINUTE));
            buf.writeIntLE(nano / 1000);
          } else {
            buf = allocator.buffer(8, 8);
            buf.writeByte((byte) 7);
            buf.writeShortLE((short) val.get(ChronoField.YEAR));
            buf.writeByte(val.get(ChronoField.MONTH_OF_YEAR));
            buf.writeByte(val.get(ChronoField.DAY_OF_MONTH));
            buf.writeByte(val.get(ChronoField.HOUR_OF_DAY));
            buf.writeByte(val.get(ChronoField.MINUTE_OF_HOUR));
            buf.writeByte(val.get(ChronoField.SECOND_OF_MINUTE));
          }
          return buf;
        });
  }

  public DataType getBinaryEncodeType() {
    return DataType.DATETIME;
  }
}
