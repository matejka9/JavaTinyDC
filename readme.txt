TinyDC
specification version: 0.001 (15.1.2011)

TinyDC is a small console project for running distributed computation in Java.
It consists of three little applications: 

tinydc.master.Master
tinydc.slave.Slave
tinydc.client.Client

They are started using these classes, and it is recommended to start
them from the command line.

In a typical setting, there is one master, many slaves, and some clients.
Each application typically runs on a different machine, but they can run
on the same (currently, at most one client and at most one slave can run on the
same machine).

Master is a central node that maintains a pool of slaves. It is also a server
for the clients (i.e. the setup is a combination of master/slave and client/server
architecture). Master quietly waits for requests from the clients and processes 
them as they arrive over the network.

Clients are controlled by the users from the console. Users enter the tasks
to be computed at the slaves. 

Slaves are quietly waiting for the tasks from master, compute them, and return
the results when completed.

Each time master receives a new task, it puts the task on the queue 
of waiting tasks. Whenever there is a free slave that can start 
computing one of the tasks waiting on the queue, master assigns 
the task to that slave. After the task computation at the slave 
finishes, the result is collected at the master and waits until 
some client connects and collects the result. When the result 
is collected, the task is finally removed from the master's queue.

Each task belongs to some task group called a job. Each task
is identified by an integer, each slave is also identified by
an integer (in addition to its IP address), and each job has
a name (character string).

Tasks are not all the same. They can require different functions,
which are called services. Each task requires exactly one service.
Each slave can provide many services and each slave can provide
different set of services. In order to use client to submit a task
that requires some service, that client must have this service
activated.

Each service consists of three classes with the following names:

tinydc.slave.services.SERVICENAME, 
tinydc.common.servicesdata.SERVICENAMEInput
tinydc.common.servicesdata.SERVICENAMEOutput

for instance, the project already contains a service Addition,
so you can find the corresponding classes Addition, AdditionInput, 
and AdditionOutput. 

The SERVICENAMEInput class should define all the data fields that need
to be transported from the client to the slave together with the
task. It can optionally also define methods
initializeFromConsole() and/or initializeDefault() so that you
can enter the data directly from the console. Alternately, they
can be read from a binary file using Serialization, if you like
to prepare some data in advance.

The SERVICENAMEOutput class should define all the data fields that need
to be transported from the slave back to the client after the computation
is finished. It can also define the method printToConsole(), which
prints the resulting data on the console. They are always also
saved to a file using Serialization for you so that you can use the
result further on.

The SERVICENAME class should define method
SERVICENAMEOutput start(SERVICENAMEInput),
which receives the object containing input data, and should return
the an object with the output data after it finishes its computation.
The method is started in a separate thread.

Please study the files Addition.java, AdditionInput.java, and AdditionOutput.java
carefully, before you proceed further on.

In this project, you can choose some of the tasks listed at the end.
They are all about modifying/extending functionality of TinyDC. 
You can choose any subset of tasks, however, the maximum score you
can earn is 25 points.

Let's look inside at the implementation of the three applications.
The sources are divided into several packages:

tinydc.master.* -- classes used only by master
tinydc.slave.* -- classes used only by slave
tinydc.client.* -- classes used only by client
tinydc.common.* -- shared classes
tinydc.common.servicesdata.* -- input and output data classes for all services
tinydc.slave.services.* -- all services

The most simple of the three programs is client. It has a simple
console-menu interface that accesses the basic functionality provided
by the client-master interface: 

----------------------------------
TinyDC client, version 0.001

current job: ax

1) change job
2) submit new task
3) get status of a submitted task
4) cancel a submitted task
5) collect finished task
6) list my submitted task
0) exit
----------------------------------

Each time a particular option is selected, client creates a new
TCP connection to master, sends a request, retrieves the response,
and closes the connection again. It informs the user about its
activity at the console. Client can be closed while the tasks are
running. It can then be started any time later to collect the results,
which will be waiting at master.

Slave is also relatively simple program. In the beginning, slave
creates a TCP connection to master to "login", i.e. to send
the list of the services it supports to master. The same thread 
then waits for TCP connections from master to receive tasks, 
and some other requests. When a new connection arrives, slave retrieves
all the information from the socket, and sometimes sends a response
back to the master over the same connection. Then it closes the
connection. Second thread is periodically sending status packets to
master in the background over UDP connection. This communication
is one-directional only and its purpose is to inform master
that the slave is still running. Whenever a task arrives, slave
starts a third thread with the required service.

Master has four threads, three are managing connections with
clients and slaves and one is producing a status information
in HTML file that can be viewed remotely using a web-browser,
if the file is saved to a location that is publicly available.
The first thread is waiting for TCP requests from clients, 
the second thread retrieves UDP datagrams containing status
packets from the slaves, and the third thread is waiting for
results that are submitted by the slaves.
Its architecture corresponds to this division:

SlavesManagement class is responsible for talking with slaves
ClientsManagement class is responsible for talking with clients
JobsManagement class holds a list of jobs and tasks for each job
TasksManagement class holds a complete list of tasks 

All three programs use configuration XML-files. These need to
be placed in the current directory. Please use these files if
you need to add more configuration options.

The communication between the three programs is realized using
XML messages. The task input and output data are sent in binary
format using serialization following the XML message.
The format of the XML messages is shown here: protocol.txt.
Master logs all the messages that it sends and receives into
a separate file for each session. Example is shown here: 
messagesAtMaster.log


1. Now, please, try to run master, slave, and client, and try to
compute 1+1 and 42-24 by submitting such two tasks from the client.
Collect the logfile, and the output files to a separate folder.
This is the first obligatory part of this project for [2 points].

2. Implement the factorial service that will compute n!, test it, 
and document it as in point 1 above. [2 points]

When finished, you can choose any combination of the following tasks:

3. Extend the Client-Master protocol so that the client
will be able to obtain a list of services provided by
the currently active slaves (both busy and free).
Add a new menu item for the console client so that
this list can be retrieved from the master and shown
to the user. [5 points]

4. Same as 3, but for list of currently active slaves
(slaveID, IPaddress, which services it implements).
[5 points]

5. Add some more interesting service and document its functionality.
For example, processing some images, or some more complicated
computation. Use your creativity. [5 points] 
more advanced solutions: [another 5 points]

5. Modify the project so that it will be possible to shut down
Master and then restart it without disrupting the service. 
All queued tasks will be stored in a file. When Master will be 
started again, it will be able to completely recover its previous 
state and then collect the finished tasks from the slaves that were 
running tasks. [10 points]

6. Modify the project so that it will be possible to shutdown Slave
and then restart it again. Partial result will be saved locally
into file. When the slave is restarted, the computation will resume
from the point where it was interrupted, and finally the result
will be sent to master. Your solution should be general (not only
for one service). You will need to modify the interface
tiny.slave.services.Service, and also Master so that it will not
reschedule the task to another slave. Implement one service that
will make use of this new feature. [10 points]

7. Modify the client so that its current functionality can be controlled
from the command line options without using the console. [5 points]

8. Implement a graphical client, i.e. the same functionality, but using
a GUI. [7 points]

9. The current communication between the programs is not secure. 
Add a security layer so that the communication will be secure
and encrypted. [7 points]

10. Modify Master so that in addition to simple HTML status report
it will also generate an image with a graphical statistics of
slaves utilization over time that will show the number of slaves,
number of tasks, and slave utilization from the time period of
Master session. [7 points]

11. Current implementation has the following feature: when the IDs exceed 
signed 32-bit integer, the application will not continue properly.
Modify the implementation reliably so that the system will not 
have to be restarted before the IDs run too high. [5 points]

12. Modify the project so that it will include user management. Each
job (and its tasks) will be controlled only by the user who started 
that job. Users have passwords, which can be changed, they are
transmitted encrypted over the network. [7 points]

13. Make all the required modifications to make it possible to send
not only data, but also the code. Currently, the classes for
each service that the user wants to use have to be present at
the slave before the task can be submitted. Allow the user to
provide his set of classes that will be transported to the slave
together with the input data. [10 points]

14. Add a scripting possibility for the client. The scripting will
allow to schedule multiple tasks in a sequence and run them in
one batch. Multiple tasks of the same kind can be started 
with one expression. Output data from some tasks can be used
in other tasks. User should have a possibility to prepare such
script to be started overnight. The user should have some 
possibility to see how far did the computation get at any time.
[12 points]

15. Implement a JSP web-based client. Submitting tasks will be possible
using a web browser (using simple HTML forms without scripting).
Scripting (in JSP) is only on the side of web-server, from where
the client will connect to the master. [10 points]

16. A different, but interesting extension based on your preference.
(needs a confirmation from us in advance).

The source code for the project is available here: tinydc0001.zip