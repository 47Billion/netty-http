Netty Http
-------
A library to develop HTTP services with [Netty](http://netty.io/). Supports the capability to route end-points based on [JAX-RS](https://jax-rs-spec.java.net/) style annotations. Implements Guava's Service interface to manage the runtime-state of the HTTP service.

Need for this library 
---------------------
[Netty](http://netty.io/) is a powerful framework to write asynchronous event-driven high-performance applications. While it is relatively easy to write a RESTFUL HTTP service using netty, the mapping between HTTP routes to handlers is
not a straight-forward task.

Mapping the routes to method handlers requires writing custom channel handlers and a lot of boilerplate code
as well as knowledge of Netty's internals in order to correctly chain different handlers. The mapping could be
error prone and tedious when a service handles a lot of end-points.

This library solves these problems using [JAX-RS](https://jax-rs-spec.java.net/) annotations to build a path routing layer on top of Netty.

Build the HTTP Library
----------------------
```
  $ git clone http://github.com/continuuity/http
  $ cd http
  $ mvn clean package
```

Setting up an HTTP Service using the library
--------------------------------------------
Setting up an HTTP service is very simple using this library:
* Implement handler methods for different HTTP requests
* Annotate the routes for each handler
* Use a builder to setup the HTTP service

Example: A simple HTTP service that responds to the `/v1/ping` endpoint can be setup as:

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
    NettyHttpService service = NettyHttpService.builder()
                               .setPort(7777)
                               .addHttpHandlers(ImmutableList.of(new PingHandler()))
                               .build();

    // Start the HTTP service
    service.startAndWait();
```

Example: Sample HTTP service that manages an application lifecycle.

```java
   // Set up handlers
   // Setting up Path annotation on a class level will be pre-pended with
   @Path("/v1/apps")
   public class ApplicationHandler extends AbstractHandler {

      // The HTTP endpoint v1/apps/deploy will be handled by the deploy method given below
      @Path("deploy")
      @POST
      public void deploy(HttpRequest request, HttpResponder responder) {
        // ..
        // Deploy application and send status
        // ..
        responder.sendStatus(HttpResponseStatus.OK);
      }

      // The HTTP endpoint v1/apps/{id}/start will be handled by the start method given below
      @Path("{id}/start")
      @POST
      public void start(HttpRequest request, HttpResponder responder, @PathParam("id") String id) {
      // The id that is passed in HTTP request will be mapped to a String via the PathParam annotation
        // ..
        // Start the application
        // ..
        responder.sendStatus(HttpResponseStatus.OK);
      }

      // The HTTP endpoint v1/apps/{id}/stop will be handled by the stop method given below
      @Path("{id}/stop")
      @POST
      public void stop(HttpRequest request, HttpResponder responder, @PathParam("id") String id) {
      // The id that is passed in HTTP request will be mapped to a String via the PathParam annotation
        // ..
        // Stop the application
        // ..
        responder.sendStatus(HttpResponseStatus.OK);
      }

      // The HTTP endpoint v1/apps/{id}/status will be handled by the status method given below
      @Path("{id}/status")
      @GET
      public void status(HttpRequest request, HttpResponder responder, @PathParam("id") String id) {
      // The id that is passed in HTTP request will be mapped to a String via the PathParam annotation
        // ..
        // Retrieve status the application
        // ..
        JsonObject status = new JsonObject();
        status.addProperty("status", "RUNNING");
        responder.sendJson(HttpResponseStatus.OK, status);
      }
   }

    // Setup HTTP service and add Handlers
    NettyHttpService service = NettyHttpService.builder()
                               .setPort(7777)
                               .addHttpHandlers(ImmutableList.of(new ApplicationHandler()))
                               .build();

    // Start the HTTP service
    service.startAndWait();
```

References
----------
* [Guava](https://code.google.com/p/guava-libraries/)
* [Jersey](https://jersey.java.net)
* [Netty](http://netty.io/)

## Contributing to netty-http

Are you interested in making netty-http better? Our development model is a simple pull-based model with a consensus building phase, similar to the Apache's voting process. If you want to help make netty-http better, by adding new features, fixing bugs, or even suggesting improvements to something that's already there, here's how you can contribute:

 * Fork netty-http into your own GitHub repository
 * Create a topic branch with an appropriate name
 * Work on your favorite feature to your content
 * Once you are satisifed, create a pull request by going to the continuuity/netty-http project.
 * Address all the review comments
 * Once addressed, the changes will be committed to the continuuity/netty-http repo.

## Contributor License Agreement ("CLA")
In order to accept your pull request, we need you to submit a CLA. You only need to do this once, so if you've done this for another Continuuity open source project, you're good to go. If you are submitting a pull request for the first time, just let us know that you have completed the CLA and we can cross-check with your GitHub username. 

Email cla@continuuity.com for a copy of the CLA, fill out the form, and send it back to cla@continuuity.com


## License
Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
