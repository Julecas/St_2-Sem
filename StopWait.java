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
import terminal.Terminal;

/**
 * Protocol 3 : Stop & Wait protocol
 *
 * @author 62633 (Put here your students' numbers)
 */
public class StopWait extends Base_Protocol implements Callbacks {

    public StopWait(Simulator _sim, NetworkLayer _net) {
        super(_sim, _net);      // Calls the constructor of Base_Protocol
        frame_expected = 0;
        next_frame_to_send = 0;

    }

    /**
     * CALLBACK FUNCTION: handle the beginning of the simulation event
     *
     * @param time current simulation time
     */
    @Override
    public void start_simulation(long time) {
        sim.Log("\nStop&Wait Protocol\n\n");
        send_next_data_packet();
    }

    public void send_next_data_packet() {

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
        

        if (sending_buffer != null && (!sim.is_sending_data())) {

            sim.cancel_ack_timer();

            
            // The ACK field of the DATA frame is always the sequence number before zero, because no packets will be received
            int ack = prev_seq(frame_expected);  //ack do anterior ao proximo frame esperado = ack atual
            Frame frame = Frame.new_Data_Frame(next_frame_to_send /*seq*/,
                    ack /* ack= the one before 0 */,
                    net.get_recvbuffsize() /* returns the buffer space available in the network layer */,
                    sending_buffer);
            

            sim.to_physical_layer(frame, false /* do not interrupt an ongoing transmission*/);
            
  
            // Transmission of next DATA frame occurs after DATA_END event is received
        }else{
            return;
        }
    }

    /**
     * CALLBACK FUNCTION: handle the end of Data frame transmission, start timer
     *
     * @param time current simulation time
     * @param seq sequence number of the Data frame transmitted
     */
    @Override
    public void handle_Data_end(long time, int seq) {
        sim.start_data_timer(seq);   
    }

    /**
     * CALLBACK FUNCTION: handle the timer event; retransmit failed frames
     *
     * @param time current simulation time
     * @param key timer key (sequence number)
     */
    @Override
    public void handle_Data_Timer(long time, int key) {
        send_data_packet(); //send same packet if timer runs 
    }

    /**
     * CALLBACK FUNCTION: handle the ack timer event; send ACK frame
     *
     * @param time current simulation time
     */
    @Override
    public void handle_ack_Timer(long time) {
        sim.to_physical_layer(receaving_buffer, false /* do not interrupt an ongoing transmission*/); //envia um ack sem data se o timer expirar

    }

    /**
     * CALLBACK FUNCTION: handle the reception of a frame from the physical
     * layer
     *
     * @param time current simulation time
     * @param frame frame received
     */
    @Override
    public void from_physical_layer(long time, Frame frame) {

        //TRATAMENTO DE DATA 
        if (frame.kind() == Frame.DATA_FRAME) {     // Check if its a data frane
            //ativação de ack timer para piggybaking

            DataFrameIF dframe = frame;  // Auxiliary variable to access the Data frame fields.

            Frame ack_frame = Frame.new_Ack_Frame(dframe.seq(), dframe.rcvbufsize()); //criar ACK frame
            receaving_buffer = ack_frame; // To store the ack frame
            
            //mesmo tratamento de ack
            if (dframe.ack() == next_frame_to_send) {

                sim.cancel_data_timer(next_frame_to_send);
                next_frame_to_send = next_seq(next_frame_to_send); // avança na seq

                send_next_data_packet();
            }
           
            if (dframe.seq() == frame_expected) {    // Check the sequence number
                // Send the frame to the network layer
                
                if (net.to_network_layer(dframe.info())) { //se for bem recebido
                    frame_expected = next_seq(frame_expected); //avança na seq
                    sim.start_ack_timer();
                }
            }
            
            //TREATAMENTO DE ACK 
            if (frame.kind() == Frame.ACK_FRAME) { //check if its a ack frame

                AckFrameIF aframe = frame;  // Auxiliary variable to access the Ack frame fields.

                if (aframe.ack() == next_frame_to_send) { //envio de data(somente) após ack

                    sim.cancel_data_timer(next_frame_to_send);
                    next_frame_to_send = next_seq(next_frame_to_send); // avança na seq
                    
                    send_next_data_packet();                    
                }
            }
        }
    }

    /**
     * CALLBACK FUNCTION: handle the end of the simulation
     *
     * @param time current simulation time
     */
    @Override
    public void end_simulation(long time) {
        sim.Log("Stopping simulation\n");
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
     * receaving buffer
     */
    private Frame receaving_buffer;
    /**
     * Sending buffer
     */
    private String sending_buffer;
    /**
     * Expected sequence number of the next data frame received
     */
    private int frame_expected;
}
