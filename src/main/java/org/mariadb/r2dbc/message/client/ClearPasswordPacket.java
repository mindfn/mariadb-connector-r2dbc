// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2020-2022 MariaDB Corporation Ab

package org.mariadb.r2dbc.message.client;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import java.nio.charset.StandardCharsets;
import org.mariadb.r2dbc.message.ClientMessage;
import org.mariadb.r2dbc.message.Context;
import org.mariadb.r2dbc.message.MessageSequence;

public final class ClearPasswordPacket implements ClientMessage {

  private final CharSequence password;
  private final MessageSequence sequencer;

  public ClearPasswordPacket(MessageSequence sequencer, CharSequence password) {
    this.sequencer = sequencer;
    this.password = password;
  }

  @Override
  public ByteBuf encode(Context context, ByteBufAllocator allocator) {
    if (password == null) return allocator.ioBuffer(0);
    ByteBuf buf = allocator.ioBuffer(password.length() * 4);
    buf.writeCharSequence(password, StandardCharsets.UTF_8);
    buf.writeByte(0);
    return buf;
  }

  @Override
  public MessageSequence getSequencer() {
    return sequencer;
  }
}
