package de.sciss.jacktest

import de.sciss.jack

import scala.scalanative.native.{sizeof, _}
import scala.scalanative.native.stdio._
import scala.scalanative.native.stdlib._

// cf. https://github.com/jackaudio/example-clients/blob/master/simple_client.c
object SimpleClient {
  import jack._
  import Jack._
  import JackOptions._
  import JackStatus._
  import JackPortFlags._

  var input_port : Ptr[jack_port_t] = _
  var output_port: Ptr[jack_port_t] = _

  final val Pi2         = 2 * Math.PI
  final val phaseInc0   = 440.0 / 48000 * Pi2
  final val phaseInc1   = phaseInc0 * 2
  final val phaseIncInc = 0.01 / 48000 * Pi2

  var phase   : Double = 0.0
  var phaseInc: Double = phaseInc0

  def glissando(n: Int, out: Ptr[jack_default_audio_sample_t]): Unit = {
    var _phase  = phase
    var _inc    = phaseInc
    var i       = 0
    while (i < n) {
      val x   = (Math.sin(_phase) * 0.5).toFloat
      out(i)  = x
      _phase  = (_phase + _inc) % Pi2
      _inc   += phaseIncInc
      if (_inc > phaseInc1) _inc = phaseInc0
      i      += 1
    }
    phase = _phase
    phaseInc = _inc
  }

  /** The process callback for this JACK application is called in a
    * special realtime thread once for each audio cycle.
    *
    * This client does nothing more than copy data from its input
    * port to its output port. It will exit when stopped by
    * the user (e.g. using Ctrl-C on a unix-ish operating system)
    */
  val process: JackProcessCallback = { (nframes: jack_nframes_t, _: Ptr[_]) =>
    // jack_default_audio_sample_t *in, *out;
//    val in  = jack_port_get_buffer(input_port , nframes).asInstanceOf[Ptr[jack_default_audio_sample_t]]
    val out = jack_port_get_buffer(output_port, nframes).cast[Ptr[jack_default_audio_sample_t]] // .asInstanceOf[Ptr[jack_default_audio_sample_t]]
//    val numBytes = sizeof[jack_default_audio_sample_t] * nframes.toInt
//    string.memcpy(out.asInstanceOf[Ptr[Byte]], in.asInstanceOf[Ptr[Byte]], numBytes)
    // System.arraycopy(in, 0, out, 0, /* sizeof[jack_default_audio_sample_t].toInt * */ nframes.toInt) // crashes SN
    glissando(nframes.toInt, out)
    0
  }

  /**
    * JACK calls this shutdown_callback if the server ever shuts down or
    * decides to disconnect the client.
    */
  val jack_shutdown: JackShutdownCallback = { _: Ptr[_] =>
    exit(1)
  }

  def main(args: Array[String]): Unit = {
    val x = jack_client_name_size
    fprintf(stdout, c"Maximum client name size is %d\n", x)
    fflush(stdout)  // why is this needed?

    val client_name: CString = c"simple"
//    const char **ports;
    val server_name: CString = null
    val options: jack_options_t = JackNullOption
    val statusPtr: Ptr[jack_status_t] = malloc(sizeof[jack_status_t]).cast[Ptr[jack_status_t]]

    /* open a client connection to the JACK server */

    val client = jack_client_open(client_name, options, statusPtr, server_name)
    val status = statusPtr(0)
//    if (client == null) {
//      fprintf (stderr, c"jack_client_open() failed, status = 0x%2.0x\n", status)
//      if ((status & JackServerFailed) != 0) {
//        fprintf (stderr, c"Unable to connect to JACK server\n")
//      }
//      exit (1)
//    }
    if ((status & JackServerStarted) != 0) {
      fprintf(stderr, c"JACK server started\n")
    }
    if ((status & JackNameNotUnique) != 0) {
      // client_name = jack_get_client_name(client)
      // fprintf (stderr, c"unique name `%s' assigned\n", client_name)
      fprintf(stderr, c"unique name assigned\n")
    }

    /* tell the JACK server to call `process()' whenever
       there is work to be done.
    */

    val DUMMY = malloc(0)
    jack_set_process_callback(client, process, DUMMY)

    /* tell the JACK server to call `jack_shutdown()' if
       it ever shuts down, either entirely, or if it
       just decides to stop calling us.
    */

    jack_on_shutdown(client, jack_shutdown, DUMMY)

    /* display the current sample rate.
     */

    // " PRIu32 "
    printf(c"engine sample rate: %d\n", jack_get_sample_rate (client))
    fflush(stdout)  // why is this needed?

    /* create two ports */

    input_port  = jack_port_register(client, c"input" , JACK_DEFAULT_AUDIO_TYPE, JackPortIsInput .toULong, 0.toULong)
    output_port = jack_port_register(client, c"output", JACK_DEFAULT_AUDIO_TYPE, JackPortIsOutput.toULong, 0.toULong)

//    if ((input_port == null) || (output_port == null)) {
//      fprintf(stderr, c"no more JACK ports available\n")
//      exit(1)
//    }

    /* Tell the JACK server that we are ready to roll.  Our
     * process() callback will start running now. */

    if (jack_activate (client) != 0) {
      fprintf(stderr, c"cannot activate client")
      exit(1)
    }

    /* Connect the ports.  You can't do this before the client is
     * activated, because we can't make connections to clients
     * that aren't running.  Note the confusing (but necessary)
     * orientation of the driver backend ports: playback ports are
     * "input" to the backend, and capture ports are "output" from
     * it.
     */

    val DUMMY1: CString = server_name
    var ports = jack_get_ports(client, DUMMY1, DUMMY1, (JackPortIsPhysical | JackPortIsOutput).toULong)
//    if (ports == null) {
//      fprintf(stderr, c"no physical capture ports\n")
//      exit(1)
//    }

    if (jack_connect(client, ports(0), jack_port_name (input_port)) != 0)
      fprintf(stderr, c"cannot connect input ports\n")

//    free(ports)
    jack_free(ports)

    ports = jack_get_ports(client, DUMMY1, DUMMY1, (JackPortIsPhysical | JackPortIsInput).toULong)
//    if (ports == null) {
//      fprintf(stderr, c"no physical playback ports\n")
//      exit(1)
//    }

    if (jack_connect(client, jack_port_name (output_port), ports(0)) != 0)
      fprintf(stderr, c"cannot connect output ports\n")

//    free(ports)
    jack_free(ports)

    /* keep running until stopped by the user */

//    printf(c"Running jack loop...\n")
    puts(c"Running jack loop...\n")
    fflush(stdout)  // why is this needed?

    // sleep (-1)
    while(true) {
      Thread.sleep(1)
    }

    /* this is never reached but if the program
       had some other way to exit besides being killed,
       they would be important to call.
    */

    jack_client_close(client)
    exit(0)
  }
}