/*
 * Sistemas de Telecomunicacoes 
 *          2022/2023
 */
package protocol;

import terminal.Simulator;
import simulator.Frame;
import simulator.AckFrameIF;
import simulator.DataFrameIF;
import simulator.NakFrameIF;
import terminal.NetworkLayer;
import terminal.Terminal;

/**
 * Protocol 4 : Go-back-N protocol
 *
 * @author 62633 (Put here your students' numbers)
 */
public class GoBackN extends Base_Protocol implements Callbacks {

    public GoBackN(Simulator _sim, NetworkLayer _net) {
        super(_sim, _net);      // Calls the constructor of Base_Protocol

        frame_expected = 0;
        next_frame_to_send = 0;
        sending_buffer = new String[sim.get_max_sequence() + 1]; 
        ack_expected = 0;
        counter_buffer = 0; //var de controlo para o sending_buffer
        nak_sent = false; //variavel de controlo que diz se o nak foi enviado

    }

    /**
     * CALLBACK FUNCTION: handle the beginning of the simulation event
     *
     * @param time current simulation time
     */
    @Override
    public void start_simulation(long time) {
        sim.Log("\nGo-Back-N Protocol\n\n");
        send_next_data_packet();

    }

//gets next data packet from network layer and sends it to the physical layer
    public void send_next_data_packet() {

        sending_buffer[prev_seq(next_frame_to_send)] = net.from_network_layer(); // buscar o proximo pacote do nivel de rede e Guardar pacote num buffer
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

            if (sim.isactive_ack_timer()) { //verificar se estou a fazer piggybacking
                sim.Log("SENDING WITH PIGGYBACKING\n");
                 sim.cancel_ack_timer();
            }
            // The ACK field of the DATA frame is always the sequence number before zero, because no packets will be received
            int ack = prev_seq(frame_expected);  //ack do anterior ao proximo frame esperado = ack atual
            Frame frame = Frame.new_Data_Frame(next_frame_to_send /*seq*/,
                    ack /* ack= the one before 0 */,
                    net.get_recvbuffsize() /* returns the buffer space available in the network layer */,
                    sending_buffer[prev_seq(next_frame_to_send)]);
            sim.to_physical_layer(frame, false /* do not interrupt an ongoing transmission*/);

            // Transmission of next DATA frame occurs after DATA_END event is received
        }
    }

    /**
     * CALLBACK FUNCTION: handle the end of Data frame transmission, start timer
     * and send next until reaching the end of the sending window.
     *
     * @param time current simulation time
     * @param seq sequence number of the Data frame transmitted
     */
    @Override
    public void handle_Data_end(long time, int seq) { //Donne
        sim.start_data_timer(seq);
        if (between(ack_expected, next_frame_to_send, add_seq(ack_expected,sim.get_send_window())))//checks if the frame is between the ack and the window 
        {
            send_next_data_packet(); // se estiver entre o ack e a window envia o proximo pacote
        }
    }
    

    /**
     * CALLBACK FUNCTION: handle the timer event; retransmit failed frames.
     *
     * @param time current simulation time
     * @param key timer key (sequence number)
     */
    @Override
    public void handle_Data_Timer(long time, int key) { //Donne fakta perceber o counter
        
        for(int n = ack_expected; n < counter_buffer; n = next_seq(n)){ // se acontecer um timeout do data_timer temos que cancelar todos os timers
            sim.cancel_data_timer(n);
        }
        next_frame_to_send = key; //key foi o frame que levou timeout portanto queremos que o proximo a enviar seja "key"
        send_data_packet(); //send same packet if timer runs 
    }

    /**
     * CALLBACK FUNCTION: handle the ack timer event; send ACK frame
     *
     * @param time current simulation time
     */
    @Override
    public void handle_ack_Timer(long time) { //donne
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
    public void from_physical_layer(long time, Frame frame) { //donne
       
                //TRATAMENTO DE FRAMES DE NAK //donne
        if (frame.kind() == Frame.NAK_FRAME){   // Check if its a NAK frame
            
            if (between(ack_expected, prev_seq(frame.ack()), prev_seq(counter_buffer)))//verifica se o elem antes do NAK está entre o ack_expected e o ultimo elemnto do buffer
            {                                                                                                           
                for(; ack_expected != frame.ack(); ack_expected = next_seq(ack_expected)){//para o loop quando ack_expected = nak recebido (para dar a volta á sequencia por exemplo)
                   
                    sim.cancel_data_timer(ack_expected);//cancela o data timer da data recebida
                    sending_buffer[ack_expected] = null;//descarta os frames depois do nak
                    
                }
                    
            }
            for (int n = ack_expected; n <= counter_buffer; n = next_seq(n))//cancels the data timers of the datas from the nak_sent to the last sent
            {
                sim.cancel_data_timer(n);
            }
            next_frame_to_send = frame.ack();//o próximo frame a enviar é o recebido com o numero de seq do nak
            
            /**     DIFF_SEQ
            * Calculates the difference between two sequence numbers taking into 
            * account the wrapping when the maximum sequence number is reached
            * @param a first sequence number
            * @param b first sequence number
            * @return number of numbers between a and b
            */
            if (diff_seq(ack_expected, next_frame_to_send) < sim.get_send_window())//se a diferença entre o ack_expected e o proximo frame a enviar for menor que a janela de envio, enviamos um pacote
            {
                send_next_data_packet();
            }
            
        }
        
        
                    //TRATAMENTO DE FRAMES DE DADOS //donne
        if (frame.kind() == Frame.DATA_FRAME) {     // Check if its a data frane
            //ativação de ack timer para piggybaking
            if (!sim.isactive_ack_timer()) {
                sim.start_ack_timer();
            }

            DataFrameIF dframe = frame;  // Auxiliary variable to access the Data frame fields.

            Frame ack_frame = Frame.new_Ack_Frame(dframe.seq(), dframe.rcvbufsize()); //criar ACK frame
            receaving_buffer = ack_frame; // To store the ack frame
            
            //receaving a normal data frame
            if (frame.seq() == frame_expected) //verifica se o data frame é o esperado
            {    
                frame_expected = next_seq(frame_expected);
                net.to_network_layer(frame.info());
                nak_sent = false;
            } else//caso contrario envia um nak
                 if (nak_sent == false) {
                    nak_sent = true;
                    sim.cancel_ack_timer();
                    sim.to_physical_layer(Frame.new_Nak_Frame(frame_expected, net.get_recvbuffsize()), false /* do not interrupt an ongoing transmission*/); //envia um ack sem data se o timer expirar
                }
            
            
            //receaving a piggybacked data frame
            if (dframe.ack() == ack_expected) { //verifica se o frame tem o ack esperado

                sim.cancel_data_timer(ack_expected);
                //next_frame_to_send = next_seq(next_frame_to_send); // avança na seq
                sending_buffer[ack_expected] = null; //removemos o elemento do buffer pois foi confirmado via piggyback

                ack_expected = next_seq(ack_expected); //avança no proximo ack esperado
                send_next_data_packet();
            }else//if it is not the one expected
            {
                if (between(ack_expected, frame.ack(), add_seq(ack_expected, sim.get_send_window())))//verifica se o ack recebido na trama de dados está entre o ack_expected a janela 
                {
                    for (; ack_expected != frame.ack(); ack_expected = next_seq(ack_expected))//até ack_expected = ack recebido pode dar a volta á seq por exemplo
                    {//podemos confirmar que recebemos tudo até este ponto mesmo que falhe algum ack 
                        sending_buffer[ack_expected] = null;//erases the frames from buffer
                        sim.cancel_data_timer(ack_expected);
                    }
                }
            }

            if (dframe.seq() == frame_expected) {    // Check the sequence number
                // Send the frame to the network layer
                if (net.to_network_layer(dframe.info())) {
                    frame_expected = next_seq(frame_expected); //avança na seq
                }
            }
        }
        
                    //TRATAMENTO DE FRAMES DE ACK //donne
        if (frame.kind() == Frame.ACK_FRAME) { //check if its a ack frame

            AckFrameIF aframe = frame;  // Auxiliary variable to access the Ack frame fields.
            
            //se o ack for o esperado 
            if (aframe.ack() == ack_expected) { 

                sim.cancel_data_timer(next_frame_to_send);
                sending_buffer[ack_expected] = null; //retiramos o frame do buffer pois foi recebido
                ack_expected = next_seq(ack_expected);

                send_next_data_packet(); //só se envia um pacote se o ack for igual ao esperado
            }
            //se o ack não for o esperado e estiver dentro da sliding window (pode acontecer se algum ack for perdido)
            if (between(ack_expected, frame.ack(), add_seq(ack_expected, sim.get_send_window())))//verifica se o ack recebido está entre o esperado e a janela
            {
                for (; ack_expected != frame.ack(); ack_expected = next_seq(ack_expected))//para o loop quando ack_expected = ack recebido (para dar a volta á sequencia por exemplo)
                {
                    sim.cancel_data_timer(ack_expected);
                    sending_buffer[ack_expected] = null;
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
    private String[] sending_buffer;
    /**
     * Expected sequence number of the next data frame received
     */
    private int frame_expected;

    /**
     * Expected sequence number of the next ack received
     */
    private int ack_expected;
    /**
     * Counter for the buffer
     */
    private int counter_buffer;
    /**
     * State of nak - if has been sent (true) false otherwise
     */
    private boolean nak_sent;
 
}
