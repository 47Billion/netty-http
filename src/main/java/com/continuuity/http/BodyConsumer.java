package com.continuuity.http;

import org.jboss.netty.buffer.ChannelBuffer;

/**
 * HttpHandler method would extend this abstract class to stream the body directly.
 * chunk method would receive the http-chunks of the body and finished would be called
 * on receipt of the last chunk.
 */
public abstract class BodyConsumer {
  /**
   * Http request content will be streamed directly to this method.
   * @param request
   * @param responder
   */
  abstract void chunk(ChannelBuffer request, HttpResponder responder);

  /**
   * This is called on the receipt of the last HttpChunk.
   * @param responder
   */
  abstract void finished(HttpResponder responder);

  /**
   * When there is exception on netty while streaming, it will be propagated to handler
   * so the handler can release resources.
   * @param cause
   */
  abstract void handleError(Throwable cause);
}
