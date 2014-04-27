package com.continuuity.http;

import org.jboss.netty.buffer.ChannelBuffer;

/**
 * HttpHandler would extend this abstract class and implement methods to stream the body directly.
 * chunk method would receive the http-chunks of the body and finished would be called
 * on receipt of the last chunk.
 */
public abstract class BodyConsumer {
  /**
   * Http request content will be streamed directly to this method.
   * @param request
   * @param responder
   */
  public abstract void chunk(ChannelBuffer request, HttpResponder responder);

  /**
   * This is called on the receipt of the last HttpChunk.
   * @param responder
   */
  public abstract void finished(HttpResponder responder);

  /**
   * When there is exception on netty while streaming, it will be propagated to handler
   * so the handler can do the cleanup.
   * @param cause
   */
  public abstract void handleError(Throwable cause);
}
