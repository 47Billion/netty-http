http
====

Framework to build HTTP service built on top of Netty. Supports capabilities to route end-points using annotations.
Implements Guava's Service interface to manage the states of the HTTP service.


Need for this framework
=======================
Netty is a powerful framework to write asynchronous event driven high performance applications. While it is
relatively easy to write a RESTFUL HTTP service using netty, the mapping between HTTP routes to handlers is
not a straight-forward task. Common way to map the routes to methods that handle route is to use regular expressions
to match the incoming route to handlers. This could be error prone and tedious while a service handles
a lot of end-points.

The problem could be alleviated by using annotations for HTTP routes that is handling a particular
end-point and to have a framework that can handle this path routing based on annotations.

This framework aims to solve the problem by using JAX-RS annotations from jersey-library for HTTP route
to handler mapping and building a path routing layer on-top of Netty HTTP.


Setting up a HTTP Service using the framework
==============================================

Setting up a HTTP service is very simple using this framework:
a) Write Handlers to that handle HTTP methods
b) Annotate the routes that each handler using annotations
c) Use a builder to setup the HTTP service


Example: A simple HTTP service that responds to /v1/ping endpoint can be setup as follows:


```java
    // Set up Handlers for Ping
    public class PingHandler extends AbstractHttpHandler {

      @Path("/v1/ping")
      @GET
      public void testGet(HttpRequest request, HttpResponder responder){
        responder.sendString(HttpResponseStatus.OK, "OK");
      }

    }

   // Setup HTTP service and add Handlers
    NettyHttpService.Builder builder = NettyHttpService.builder();
    builder.setPort(7777);

    List<HttpHandler> handlers = Lists.newArrayList();
    handlers.add(new PingHandler());

    //Add HTTP handlers
    builder.addHttpHandlers(handlers);

    NettyHttpService service = builder.build();
    service.startAndWait();

```