// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2020-2022 MariaDB Corporation Ab

package org.mariadb.r2dbc.codec;

import io.r2dbc.spi.*;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import org.mariadb.r2dbc.ExceptionFactory;
import org.mariadb.r2dbc.codec.list.*;
import org.mariadb.r2dbc.message.Context;
import org.mariadb.r2dbc.message.Protocol;
import org.mariadb.r2dbc.util.Assert;
import org.mariadb.r2dbc.util.BindValue;

public final class Codecs {

  private static final Map<R2dbcType, Codec<?>> r2dbcTypeMapper = r2dbcTypeToDataTypeMap();
  private static final Map<Class<?>, Codec<?>> codecMapper = classToCodecMap();

  public static final Codec<?>[] LIST =
      new Codec<?>[] {
        BigDecimalCodec.INSTANCE,
        BigIntegerCodec.INSTANCE,
        BooleanCodec.INSTANCE,
        ByteBufferCodec.INSTANCE,
        BlobCodec.INSTANCE,
        ByteArrayCodec.INSTANCE,
        ByteCodec.INSTANCE,
        BitSetCodec.INSTANCE,
        ClobCodec.INSTANCE,
        DoubleCodec.INSTANCE,
        LongCodec.INSTANCE,
        FloatCodec.INSTANCE,
        IntCodec.INSTANCE,
        LocalDateCodec.INSTANCE,
        LocalDateTimeCodec.INSTANCE,
        LocalTimeCodec.INSTANCE,
        DurationCodec.INSTANCE,
        ShortCodec.INSTANCE,
        StreamCodec.INSTANCE,
        StringCodec.INSTANCE
      };

  public static BindValue encodeNull(Class<?> type, int index) {
    return new BindValue(codecFromClass(type, index), BindValue.NULL_VALUE);
  }

  public static BindValue encode(
      Object value, int index, Protocol protocol, ExceptionFactory factory, Context context) {

    Codec<?> codec = StringCodec.INSTANCE;
    Object parameterValue = value;

    if (value instanceof Parameter) {
      Parameter parameter = (Parameter) value;
      parameterValue = parameter.getValue();

      if (parameterValue == null) {
        if (parameter.getType() instanceof R2dbcType) {
          codec = codecFromR2dbcType((R2dbcType) parameter.getType());
          return new BindValue(codec, BindValue.NULL_VALUE);
        }
        return new BindValue(codec, BindValue.NULL_VALUE);
      }
    }

    codec = codecFromClass(parameterValue.getClass(), index);

    if (parameterValue == null) {
      return new BindValue(codec, BindValue.NULL_VALUE);
    }

    if (protocol == Protocol.TEXT)
      return codec.encodeText(context.getByteBufAllocator(), parameterValue, context, factory);
    return codec.encodeBinary(context.getByteBufAllocator(), parameterValue, factory);
  }

  public static Codec<?> codecFromClass(Class<?> javaType, int index) {
    if (javaType == null) return StringCodec.INSTANCE;
    Codec<?> codec = codecMapper.get(javaType);
    if (codec != null) return codec;
    codec = codecByClass(javaType, index);
    if (codec != null) return codec;
    return StringCodec.INSTANCE;
  }

  public static Codec<?> codecFromR2dbcType(R2dbcType type) {
    Assert.requireNonNull(type, "type must not be null");
    Codec<?> codec = r2dbcTypeMapper.get(type);
    if (codec != null) return codec;
    return StringCodec.INSTANCE;
  }

  private static Map<R2dbcType, Codec<?>> r2dbcTypeToDataTypeMap() {
    Map<R2dbcType, Codec<?>> myMap = new HashMap<>();
    myMap.put(R2dbcType.NCHAR, StringCodec.INSTANCE);
    myMap.put(R2dbcType.CHAR, StringCodec.INSTANCE);
    myMap.put(R2dbcType.NVARCHAR, StringCodec.INSTANCE);
    myMap.put(R2dbcType.VARCHAR, StringCodec.INSTANCE);
    myMap.put(R2dbcType.CLOB, ClobCodec.INSTANCE);
    myMap.put(R2dbcType.NCLOB, ClobCodec.INSTANCE);
    myMap.put(R2dbcType.BOOLEAN, BooleanCodec.INSTANCE);
    myMap.put(R2dbcType.TINYINT, ByteCodec.INSTANCE);
    myMap.put(R2dbcType.BINARY, ByteArrayCodec.INSTANCE);
    myMap.put(R2dbcType.VARBINARY, ByteArrayCodec.INSTANCE);
    myMap.put(R2dbcType.BLOB, BlobCodec.INSTANCE);
    myMap.put(R2dbcType.INTEGER, IntCodec.INSTANCE);
    myMap.put(R2dbcType.SMALLINT, ShortCodec.INSTANCE);
    myMap.put(R2dbcType.BIGINT, BigIntegerCodec.INSTANCE);
    myMap.put(R2dbcType.NUMERIC, BigDecimalCodec.INSTANCE);
    myMap.put(R2dbcType.DECIMAL, BigDecimalCodec.INSTANCE);
    myMap.put(R2dbcType.FLOAT, FloatCodec.INSTANCE);
    myMap.put(R2dbcType.REAL, FloatCodec.INSTANCE);
    myMap.put(R2dbcType.DOUBLE, DoubleCodec.INSTANCE);
    myMap.put(R2dbcType.DATE, LocalDateCodec.INSTANCE);
    myMap.put(R2dbcType.TIME, LocalTimeCodec.INSTANCE);
    myMap.put(R2dbcType.TIME_WITH_TIME_ZONE, LocalTimeCodec.INSTANCE);
    myMap.put(R2dbcType.TIMESTAMP, LocalDateTimeCodec.INSTANCE);
    myMap.put(R2dbcType.TIMESTAMP_WITH_TIME_ZONE, LocalDateTimeCodec.INSTANCE);
    return myMap;
  }

  private static Map<Class<?>, Codec<?>> classToCodecMap() {
    Map<Class<?>, Codec<?>> myMap = new HashMap<>();
    myMap.put(String.class, StringCodec.INSTANCE);
    myMap.put(Clob.class, ClobCodec.INSTANCE);
    myMap.put(InputStream.class, StreamCodec.INSTANCE);
    myMap.put(Boolean.class, BooleanCodec.INSTANCE);
    myMap.put(byte[].class, ByteArrayCodec.INSTANCE);
    myMap.put(Blob.class, BlobCodec.INSTANCE);
    myMap.put(ByteBuffer.class, ByteBufferCodec.INSTANCE);
    myMap.put(BitSet.class, BitSetCodec.INSTANCE);
    myMap.put(Integer.class, IntCodec.INSTANCE);
    myMap.put(Short.class, ShortCodec.INSTANCE);
    myMap.put(BigInteger.class, BigIntegerCodec.INSTANCE);
    myMap.put(Long.class, LongCodec.INSTANCE);
    myMap.put(BigDecimal.class, BigDecimalCodec.INSTANCE);
    myMap.put(Float.class, FloatCodec.INSTANCE);
    myMap.put(Double.class, DoubleCodec.INSTANCE);
    myMap.put(LocalDate.class, LocalDateCodec.INSTANCE);
    myMap.put(LocalTime.class, LocalTimeCodec.INSTANCE);
    myMap.put(Duration.class, DurationCodec.INSTANCE);
    myMap.put(LocalDateTime.class, LocalDateTimeCodec.INSTANCE);
    return myMap;
  }

  public static Codec<?> codecByClass(Class<?> value, int index) {
    for (Codec<?> codec : Codecs.LIST) {
      if (codec.canEncode(value)) {
        return codec;
      }
    }
    throw new IllegalArgumentException(
        String.format("No encoder for class %s (parameter at index %s) ", value.getName(), index));
  }
}
