.. _futures-java:

Futures (Java)
===============

.. sidebar:: Contents

   .. contents:: :local:

Introduction
------------

In Akka, a `Future <http://en.wikipedia.org/wiki/Futures_and_promises>`_ is a data structure used to retrieve the result of some concurrent operation. This operation is usually performed by an ``Actor`` or by the ``Dispatcher`` directly. This result can be accessed synchronously (blocking) or asynchronously (non-blocking).

Use with Actors
---------------

There are generally two ways of getting a reply from an ``UntypedActor``: the first is by a sent message (``actorRef.tell(msg)``), which only works if the original sender was an ``UntypedActor``) and the second is through a ``Future``.

Using the ``ActorRef``\'s ``ask`` method to send a message will return a Future. To wait for and retrieve the actual result the simplest method is:

.. includecode:: code/akka/docs/future/FutureDocTestBase.java
   :include: imports1,ask-blocking

This will cause the current thread to block and wait for the ``UntypedActor`` to 'complete' the ``Future`` with it's reply. Blocking is discouraged though as it can cause performance problem.
The blocking operations are located in ``Await.result`` and ``Await.ready`` to make it easy to spot where blocking occurs. Alternatives to blocking are discussed further within this documentation.
Also note that the ``Future`` returned by an ``UntypedActor`` is a ``Future<Object>`` since an ``UntypedActor`` is dynamic. That is why the cast to ``String`` is used in the above sample.

Use Directly
------------

A common use case within Akka is to have some computation performed concurrently without needing the extra utility of an ``UntypedActor``. If you find yourself creating a pool of ``UntypedActor``\s for the sole reason of performing a calculation in parallel, there is an easier (and faster) way:

.. includecode:: code/akka/docs/future/FutureDocTestBase.java
   :include: imports2,future-eval

In the above code the block passed to ``future`` will be executed by the default ``Dispatcher``, with the return value of the block used to complete the ``Future`` (in this case, the result would be the string: "HelloWorld"). Unlike a ``Future`` that is returned from an ``UntypedActor``, this ``Future`` is properly typed, and we also avoid the overhead of managing an ``UntypedActor``.

Functional Futures
------------------

Akka's ``Future`` has several monadic methods that are very similar to the ones used by ``Scala``'s collections. These allow you to create 'pipelines' or 'streams' that the result will travel through.

Future is a Monad
^^^^^^^^^^^^^^^^^

The first method for working with ``Future`` functionally is ``map``. This method takes a ``Function`` which performs some operation on the result of the ``Future``, and returning a new result. The return value of the ``map`` method is another ``Future`` that will contain the new result:

.. includecode:: code/akka/docs/future/FutureDocTestBase.java
   :include: imports2,map

In this example we are joining two strings together within a Future. Instead of waiting for f1 to complete, we apply our function that calculates the length of the string using the ``map`` method. Now we have a second Future, f2, that will eventually contain an ``Integer``. When our original ``Future``, f1, completes, it will also apply our function and complete the second Future with it's result. When we finally ``get`` the result, it will contain the number 10. Our original Future still contains the string "HelloWorld" and is unaffected by the ``map``.

Something to note when using these methods: if the ``Future`` is still being processed when one of these methods are called, it will be the completing thread that actually does the work. If the ``Future`` is already complete though, it will be run in our current thread. For example:

.. includecode:: code/akka/docs/future/FutureDocTestBase.java
   :include: map2

The original ``Future`` will take at least 0.1 second to execute now, which means it is still being processed at the time we call ``map``. The function we provide gets stored within the ``Future`` and later executed automatically by the dispatcher when the result is ready.

If we do the opposite:

.. includecode:: code/akka/docs/future/FutureDocTestBase.java
   :include: map3

Our little string has been processed long before our 0.1 second sleep has finished. Because of this, the dispatcher has moved onto other messages that need processing and can no longer calculate the length of the string for us, instead it gets calculated in the current thread just as if we weren't using a ``Future``.

Normally this works quite well as it means there is very little overhead to running a quick function. If there is a possibility of the function taking a non-trivial amount of time to process it might be better to have this done concurrently, and for that we use ``flatMap``:

.. includecode:: code/akka/docs/future/FutureDocTestBase.java
   :include: flat-map

Now our second Future is executed concurrently as well. This technique can also be used to combine the results of several Futures into a single calculation, which will be better explained in the following sections.

Composing Futures
^^^^^^^^^^^^^^^^^

It is very often desirable to be able to combine different Futures with eachother, below are some examples on how that can be done in a non-blocking fashion.

.. includecode:: code/akka/docs/future/FutureDocTestBase.java
   :include: imports3,sequence

To better explain what happened in the example, ``Future.sequence`` is taking the ``Iterable<Future<Integer>>`` and turning it into a ``Future<Iterable<Integer>>``. We can then use ``map`` to work with the ``Iterable<Integer>`` directly, and we aggregate the sum of the ``Iterable``.

The ``traverse`` method is similar to ``sequence``, but it takes a sequence of ``A``s and applies a function from ``A`` to ``Future<B>`` and returns a ``Future<Iterable<B>>``, enabling parallel ``map`` over the sequence, if you use ``Futures.future`` to create the ``Future``.

.. includecode:: code/akka/docs/future/FutureDocTestBase.java
   :include: imports4,traverse

It's as simple as that!

Then there's a method that's called ``fold`` that takes a start-value, a sequence of ``Future``:s and a function from the type of the start-value, a timeout, and the type of the futures and returns something with the same type as the start-value, and then applies the function to all elements in the sequence of futures, non-blockingly, the execution will run on the Thread of the last completing Future in the sequence.

.. includecode:: code/akka/docs/future/FutureDocTestBase.java
   :include: imports5,fold

That's all it takes!


If the sequence passed to ``fold`` is empty, it will return the start-value, in the case above, that will be empty String. In some cases you don't have a start-value and you're able to use the value of the first completing Future in the sequence as the start-value, you can use ``reduce``, it works like this:

.. includecode:: code/akka/docs/future/FutureDocTestBase.java
   :include: imports6,reduce

Same as with ``fold``, the execution will be done by the Thread that completes the last of the Futures, you can also parallize it by chunking your futures into sub-sequences and reduce them, and then reduce the reduced results again.

This is just a sample of what can be done.

Exceptions
----------

Since the result of a ``Future`` is created concurrently to the rest of the program, exceptions must be handled differently. It doesn't matter if an ``UntypedActor`` or the dispatcher is completing the ``Future``, if an ``Exception`` is caught the ``Future`` will contain it instead of a valid result. If a ``Future`` does contain an ``Exception``, calling ``Await.result`` will cause it to be thrown again so it can be handled properly.