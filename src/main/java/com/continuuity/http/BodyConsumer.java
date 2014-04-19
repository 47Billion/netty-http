package com.continuuity.http;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.handler.codec.http.HttpMessage;

/**
 * HttpHandler method would implement BodyConsumer for streaming the request
 */
public interface BodyConsumer {
  /**
   *Http message and the HttpChunks will be streamed to this method directly.
   */
  void chunk(ChannelBuffer request, HttpResponder responder);

  /**
   * This is called on the receipt of last HttpChunk.
   * @param responder
   */
  void finished(HttpResponder responder);
}
