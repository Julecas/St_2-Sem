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

        if (sending_buffer[next_frame_to_send] == null) {
            if (net.has_more_packets_to_send()) {
                sending_buffer[next_frame_to_send] = net.from_network_layer();
            } else {
                return;
            }
        }
        send_data_packet();
    }

    /* 
    * Fetches the network layer for the next packet and starts it transmission
     * @return true is started data frame transmission, false otherwise
     */
    private void send_data_packet() { //donne
        // We can only send one Data packet at a time
        //   you must wait for the DATA_END event before transmitting another one
        //   otherwise the first packet is lost in the channel

        //verificar se ja estou a enviar data
        if (!sim.is_sending_data()) { //verificar se estou a fazer piggybacking

            sim.cancel_ack_timer();

            // The ACK field of the DATA frame is always the sequence number before zero, because no packets will be received
            int ack = prev_seq(frame_expected);  //ack do anterior ao proximo frame esperado = ack atual (redundante mas ajuda a perceber) 
            Frame frame = Frame.new_Data_Frame(next_frame_to_send /*seq*/,
                    ack /* ack= the one before 0 */,
                    net.get_recvbuffsize() /* returns the buffer space available in the network layer */,
                    sending_buffer[next_frame_to_send]);

            sim.to_physical_layer(frame, false /* do not interrupt an ongoing transmission*/);

            next_frame_to_send = next_seq(next_frame_to_send); //incremento 

        }

        // Transmission of next DATA frame occurs after DATA_END event is received
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
        if (between(ack_expected, next_frame_to_send, add_seq(ack_expected, sim.get_send_window())))//checks if the frame is between the ack and the window 
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
    public void handle_Data_Timer(long time, int key) { //Donne 

        for (int n = next_seq(key); between(next_seq(key), n, next_frame_to_send); n = next_seq(n)) { // se acontecer um timeout do data_timer temos que cancelar todos os timers
            sim.cancel_data_timer(n);
        }
        next_frame_to_send = key; //key foi o frame que levou timeout portanto queremos que o proximo a enviar seja "key"
        send_next_data_packet(); //send same packet if timer runs 
    }

    /**
     * CALLBACK FUNCTION: handle the ack timer event; send ACK frame
     *
     * @param time current simulation time
     */
    @Override
    public void handle_ack_Timer(long time) { //donne

        Frame ack_frame = Frame.new_Ack_Frame(prev_seq(frame_expected), net.get_recvbuffsize());
        if (!sim.is_sending_data()) {
            sim.to_physical_layer(ack_frame, false /* do not interrupt an ongoing transmission*/); //envia um ack sem data se o timer expirar
        } else {
            sim.Log("Could not send a ack\n");
        }

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
        if (frame.kind() == Frame.NAK_FRAME) {   // Check if its a NAK frame

            //if (!sim.isactive_data_timer(frame.nak())) {  //ATENÇÃO ALTEREI ISTO

                for (int i = ack_expected; between(ack_expected, i, next_frame_to_send); i = next_seq(i)) {//para o loop quando ack_expected = nak recebido (para dar a volta á sequencia por exemplo)

                    if (between(ack_expected, i, frame.nak())) {
                        sending_buffer[i] = null;//descarta os frames antes do nak
                    }

                }
                for (int i = 0; i <= sim.get_max_sequence(); i++) { //TOU A CANCELAR TUDO
                    sim.cancel_data_timer(i);//cancela o data timer da data recebida
                }

                next_frame_to_send = frame.nak();//o próximo frame a enviar é o recebido com o numero de seq do nak
                ack_expected = frame.nak();
                send_next_data_packet();

           // }
        }

        //TRATAMENTO DE FRAMES DE DADOS //donne
        if (frame.kind() == Frame.DATA_FRAME) {     // Check if its a data frane

            //receaving a normal data frame
            if (frame.seq() == frame_expected) //verifica se o data frame é o esperado
            {
                sim.start_ack_timer();
                if (net.to_network_layer(frame.info()));
                {
                    frame_expected = next_seq(frame_expected);
                }

                nak_sent = false;
            } else if (nak_sent == false && between(frame_expected, frame.seq(), add_seq(frame_expected, sim.get_send_window())) && !sim.is_sending_data()) {  //caso contrario envia um nak  && !sim.is_sending_data()

                nak_sent = true;
                sim.cancel_ack_timer();
                sim.to_physical_layer(
                        Frame.new_Nak_Frame(frame_expected, net.get_recvbuffsize()),
                        false /* do not interrupt an ongoing transmission*/); //envia um ack sem data se o timer expirar

            } else {  //se for um pacote retransmitido

                Frame ack_frame = Frame.new_Ack_Frame(prev_seq(frame_expected), net.get_recvbuffsize());
                sim.to_physical_layer(ack_frame, false /* do not interrupt an ongoing transmission*/); //envia um ack sem data se o timer expirar   
            }
            
            //receaving a piggybacked ack data frame        AQUI ESTOU A VER O ACK 
            if (sim.isactive_data_timer(frame.ack())) {  //ATENÇÃO ALTEREI ISTO

                if (between(
                        ack_expected,
                        frame.ack(),
                        next_frame_to_send))//verifica se o ack recebido está entre o esperado e a janela
                {
                    for (; ack_expected != next_seq(frame.ack()); ack_expected = next_seq(ack_expected))//para o loop quando ack_expected = ack recebido (para dar a volta á sequencia por exemplo)
                    {
                        sim.cancel_data_timer(ack_expected);//tramas confirmadas
                        sending_buffer[ack_expected] = null;
                    }

                    if (ack_expected == next_frame_to_send) {

                        send_next_data_packet(); //só se envia um pacote se o ack for igual ao esperado
                    }
                }
            }

        }

        //TRATAMENTO DE FRAMES DE ACK //donne
        if (frame.kind() == Frame.ACK_FRAME) { //check if its a ack frame

            //se o ack não for o esperado e estiver dentro da sliding window (pode acontecer se algum ack for perdido)
            if (between(ack_expected,
                    frame.ack(),
                    next_frame_to_send))//verifica se o ack recebido está entre o esperado e a janela
            {
                for (; ack_expected != next_seq(frame.ack()); ack_expected = next_seq(ack_expected))//para o loop quando ack_expected = ack recebido (para dar a volta á sequencia por exemplo)
                {
                    sim.cancel_data_timer(ack_expected);
                    sending_buffer[ack_expected] = null;
                }

                if (ack_expected == next_frame_to_send) { // quando o ack experado for = ao proximo pacote a enviar
                    send_next_data_packet(); //só se envia um pacote se o ack for igual ao esperado
                }
            }

        }

    }

    /* CALLBACK FUNCTION: handle the end of the simulation
        *
        * @param time current simulation time
        
     */
    @Override
    public void end_simulation(long time
    ) {
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
     * State of nak - if has been sent (true) false otherwise
     */
    private boolean nak_sent;

}
