package com.continuuity.http;
import java.lang.reflect.Method;

/**
 *
 */
public class HttpMethodInfo {

  private Method method;
  private HttpHandler handler;
  private Object[] args;
  private WrappedHttpResponder responder;

  HttpMethodInfo(Method method, HttpHandler handler, Object[] args){
    this.method = method;
    this.handler = handler;
    this.args = args;
  }

  public WrappedHttpResponder getResponder() {
    return responder;
  }

  public void setResponder(WrappedHttpResponder responder) {
    this.responder = responder;
  }

  public Method getMethod() {
    return method;
  }

  public HttpHandler getHandler() {
    return handler;
  }

  public Object[] getArgs() {
    return args;
  }
}
