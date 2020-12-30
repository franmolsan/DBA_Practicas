/*
 * Práctica 3 DBA
 * Grupo ArcelorMittal
 * Curso 2020-2021
 */
package practica3_DBA;

import static ACLMessageTools.ACLMessageTools.getDetailsLARVA;
import static ACLMessageTools.ACLMessageTools.getJsonContentACLM;
import Map2D.Map2DGrayscale;
import YellowPages.YellowPages;
import com.eclipsesource.json.JsonObject;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import java.util.ArrayList;

/**
 *
 * @author Francisco José Molina Sánchez, Pedro Serrano Pérez,
 *         Miguel Ángel Molina Jordán
 */
public class Seeker extends DroneDelMundo{
    final static String TIPO = "SEEKER";
    // atributos heredados del AgenteDrone:
    
    // protected YellowPages yp;
    // protected String estado, servicio, mundo;
    // protected String worldManager, convID;
    // protected boolean hayError;
    // protected ACLMessage in, out;

    
    @Override
    public void plainExecute() {
        plainWithErrors();
    }
  /*  
        @Override
    public void takeDown() {
        super.takeDown();
        Info("Taking down");
        Info("Exit LARVA");
        //in = enviarCancelA(_identitymanager);
        Info ("cancel LARVA contenido: "+ in.getContent());
        informarCancelacion();
        Info ("envio cancelación a identity manager");
        Info(in.getContent());
    }
*/
    public void plainWithErrors() {
        // Basic iteration
        switch (estado.toUpperCase()) {
            case "CHECKIN-LARVA":
                Info("Duermo");
                in = blockingReceive();
                convID = in.getConversationId();
                Info("Despierto: " + in.getContent());
                
                Info("antes checkin");
                Info("Checkin in LARVA with " + _identitymanager);
                in = suscribirseA(_identitymanager); // As seen in slides
                Info("despues checkin");
                hayError = (in.getPerformative() != ACLMessage.INFORM);
                if (hayError) {
                    Info("\t" + ACLMessage.getPerformative(in.getPerformative())
                            + " Checkin failed due to " + getDetailsLARVA(in));
                    estado = "EXIT";
                    break;
                }
                estado = "SUBSCRIBE-WM";
                Info("\tCheckin ok");
                break;
            /*case "ESPERAR-LISTENER":
                
                hayError = (in.getPerformative() != ACLMessage.QUERY_IF);
                if (hayError) {
                    Info("\t" + ACLMessage.getPerformative(in.getPerformative())
                            + " Checkin failed due to " + getDetailsLARVA(in));
                    estado = "EXIT";
                    break;
                }

                estado = "ESPERAR-LISTENER";
                Info("\tListo");
                estado = "SUBSCRIBE-WM";
                break;*/
            case "SUBSCRIBE-WM":
                Info("Retrieve who is my WM");
                // First update Yellow Pages
                in = obtenerYP(_identitymanager); // As seen oon slides
                Info("YP obtenidas");
                hayError = in.getPerformative() != ACLMessage.INFORM;
                if (hayError) {
                    Info("\t" + ACLMessage.getPerformative(in.getPerformative())
                            + " Query YellowPages failed due to " + getDetailsLARVA(in));
                    estado = "CHECKOUT-LARVA";
                    break;
                }
                yp = new YellowPages();
                yp.updateYellowPages(in);
                // It might be the case that YP are right but we dont find an appropriate service for us, then leave
                if (yp.queryProvidersofService(servicio).isEmpty()) {
                    Info("\t" + "There is no agent providing the service " + servicio);
                    estado = "CHECKOUT-LARVA";
                    break;
                }
                // Choose one of the available service providers, i.e., the first one
                worldManager = yp.queryProvidersofService(servicio).iterator().next();

                // Keep the Conversation ID and spread it amongs the team members
                // Move on to get the map
                estado = "SUBSCRIBE-SEEKER";
                break;
            case "SUBSCRIBE-SEEKER":
                in = suscribirseComo(TIPO);
                hayError = in.getPerformative() != ACLMessage.INFORM;
                if (hayError) {
                    Info(ACLMessage.getPerformative(in.getPerformative())
                            + " Could not subscribe as SEEKER to "
                            + worldManager + " due to " + getDetailsLARVA(in));
                    estado = "CANCEL-WM";
                    break;
                }
                informarSetupCompletado();
                estado = "LOGIN-WM";
                break;
            case "LOGIN-WM":
                int posx = 20;
                int posy = 30;
                ArrayList<String> sensores = new ArrayList<>();
                //in = realizarLoginWM(sensores, posx, posy);
                Info(in.getContent());
                estado = "ESPERAR-ORDEN";
                break;
            case "ESPERAR-ORDEN":
                Info("Esperando Listener");
                in = blockingReceive();
                Info(in.getContent());
                hayError = in.getPerformative() != ACLMessage.INFORM;
                if (hayError) {
                    Info(ACLMessage.getPerformative(in.getPerformative())
                            + " Error en mensaje");
                    estado = "CANCEL-WM";
                    break;
                }
                if (in.getContent().equals("turnOff")){
                    estado = "CHECKOUT-LARVA";
                    break;
                }
                else if(in.getContent().equals("")){
                    break;
                }
                else{
                    estado = "CHECKOUT-LARVA";
                    break;
                }
            /*case "CANCEL-WM":
                Info("Closing the game");
                in = enviarCancelA(worldManager);
                estado = "CHECKOUT-LARVA";
                break;*/
            case "CHECKOUT-LARVA":
                Info("Exit LARVA");
                in = enviarCancelA(_identitymanager);
                informarCancelacion();
                estado = "EXIT";
                break;
            case "EXIT":
                Info("The agent dies");
                _exitRequested = true;
                break;
        }
    }
    
}