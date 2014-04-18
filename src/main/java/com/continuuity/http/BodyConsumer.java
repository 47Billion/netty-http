package com.continuuity.http;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.handler.codec.http.HttpMessage;

/**
 *
 */
public interface BodyConsumer {

  void chunk(ChannelBuffer request, HttpResponder responder);
  void finished(HttpResponder responder);
}
