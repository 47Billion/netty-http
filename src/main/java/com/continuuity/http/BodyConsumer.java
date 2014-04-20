package com.continuuity.http;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.handler.codec.http.HttpMessage;

/**
 * HttpHandler would implement this interface to stream the body directly.
 * chunk method would receive the http-chunks of the body and finished would be called
 * on receipt of the last chunk.
 */
public interface BodyConsumer {
  /**
   * Http request content will be streamed directly to this method.
   * @param request
   * @param responder
   */
  void chunk(ChannelBuffer request, HttpResponder responder);

  /**
   * This is called on the receipt of the last HttpChunk.
   * @param responder
   */
  void finished(HttpResponder responder);
}
