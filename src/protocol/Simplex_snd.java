/*
 * Sistemas de Telecomunicacoes 
 *          2022/2023
 */
package protocol;

import simulator.AckFrameIF;
import simulator.DataFrameIF;
import terminal.Simulator;
import simulator.Frame;
import terminal.NetworkLayer;

/**
 * Protocol 2 : Simplex Sender protocol which does not receive frames
 *
 * @author 62633
 */
public class Simplex_snd extends Base_Protocol implements Callbacks {

    public Simplex_snd(Simulator _sim, NetworkLayer _net) {
        super(_sim, _net);      // Calls the constructor of Base_Protocol
        next_frame_to_send = 0;
        // ...
    }

    /**
     * CALLBACK FUNCTION: handle the beginning of the simulation event
     *
     * @param time current simulation time
     */
    @Override
    public void start_simulation(long time) { //DONNE
        sim.Log("\nSimplex Sender Protocol\n\tOnly send data!\n\n");
        send_next_data_packet();

    }

    public void send_next_data_packet(){
        
        sending_buffer = net.from_network_layer(); // buscar o proximo pacote do nivel de rede e Guardar pacote num buffer
        send_data_packet();
    }
    
    /* 
    * Fetches the network layer for the next packet and starts it transmission
     * @return true is started data frame transmission, false otherwise
     */
    private void send_data_packet() {
        // We can only send one Data packet at a time
        //   you must wait for the DATA_END event before transmitting another one
        //   otherwise the first packet is lost in the channel
        //sending_buffer = net.from_network_layer(); // Guardar pacote num buffer
        if (sending_buffer != null) {
            // The ACK field of the DATA frame is always the sequence number before zero, because no packets will be received
            Frame frame = Frame.new_Data_Frame(next_frame_to_send /*seq*/,
                    prev_seq(0) /* ack= the one before 0 */,
                    net.get_recvbuffsize() /* returns the buffer space available in the network layer */,
                    sending_buffer);
            sim.to_physical_layer(frame, false /* do not interrupt an ongoing transmission*/);
            //next_frame_to_send = next_seq(next_frame_to_send);
            // Transmission of next DATA frame occurs after DATA_END event is received
        }
    }

    /**
     * CALLBACK FUNCTION: handle the end of Data frame transmission, start timer
     *
     * @param time current simulation time
     * @param seq sequence number of the Data frame transmitted
     */
    @Override
    public void handle_Data_end(long time, int seq) { //DONNE
         sim.start_data_timer(seq);
           
    }

    /**
     * CALLBACK FUNCTION: handle the data timer event; retransmit failed frames
     *
     * @param time current simulation time
     * @param key timer key (sequence number)
     */
    @Override
    public void handle_Data_Timer(long time, int key) { //acabou o timer //DONNE
        //FAZER 
        send_data_packet(); //send same packet if timer runs out
    }

    /**
     * CALLBACK FUNCTION: handle the ack timer event; send ACK frame
     *
     * @param time current simulation time
     */
    @Override
    public void handle_ack_Timer(long time) { //DONNE
        //DO NOTHING
    }

    /**
     * CALLBACK FUNCTION: handle the reception of a frame from the physical
     * layer
     *
     * @param time current simulation time
     * @param frame frame received
     */
    @Override
    public void from_physical_layer(long time, Frame frame) { //DONNE

        if (frame.kind() == Frame.ACK_FRAME) {
            AckFrameIF aframe = frame;  // Auxiliary variable to access the Ack frame fields.
          
                sim.cancel_data_timer(next_frame_to_send);                
                next_frame_to_send = next_seq( next_frame_to_send );
                
                send_next_data_packet();
        }
    }

    /**
     * CALLBACK FUNCTION: handle the end of the simulation
     *
     * @param time current simulation time
     */
    @Override
    public void end_simulation(long time) { //DONNE
        sim.Log("Stopping simulation\n");
        //sim.stop();
    }

    /* Variables */
    /**
     * Reference to the simulator (Terminal), to get the configuration and send
     * commands
     */
    //final Simulator sim;  -  Inherited from Base_Protocol
    /**
     * Reference to the network layer, to send a receive packets
     */
    //final NetworkLayer net;    -  Inherited from Base_Protocol
    /**
     * Sequence number of the next data frame
     */
    private int next_frame_to_send;

    /**
     * Sending buffer
     */
    private String sending_buffer;
}
