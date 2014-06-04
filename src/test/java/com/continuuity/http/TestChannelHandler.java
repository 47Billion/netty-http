package com.continuuity.http;

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelDownstreamHandler;
import org.jboss.netty.handler.codec.http.HttpResponse;

/**
 * Test ChannelHandler that adds a default header to every response.
 */
public class TestChannelHandler extends SimpleChannelDownstreamHandler {
  protected static final String HEADER_FIELD = "testHeaderField";
  protected static final String HEADER_VALUE = "testHeaderValue";

  @Override
  public void writeRequested(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
    Object message = e.getMessage();
    if (!(message instanceof HttpResponse)) {
      super.writeRequested(ctx, e);
      return;
    }
    HttpResponse response = (HttpResponse) message;
    response.addHeader(HEADER_FIELD, HEADER_VALUE);
    super.writeRequested(ctx, e);
  }
}
