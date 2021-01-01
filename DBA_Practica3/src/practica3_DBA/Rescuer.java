/*
 * Práctica 3 DBA
 * Grupo ArcelorMittal
 * Curso 2020-2021
 */
package practica3_DBA;

import static ACLMessageTools.ACLMessageTools.getDetailsLARVA;
import YellowPages.YellowPages;
import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Francisco José Molina Sánchez
 */
public class Rescuer extends DroneDelMundo{
    // atributos heredados del AgenteDrone:
    
    // protected YellowPages yp;
    // protected String estado, servicio, mundo;
    // protected String worldManager, convID;
    // protected boolean hayError;
    // protected ACLMessage in, out;

    @Override
    public void setup() {
        super.setup();
        tipo = "RESCUER"; 
    }
    
    @Override
    public void plainExecute() {
        plainWithErrors();
    }

    public void plainWithErrors() {
        // Basic iteration
        switch (estado.toUpperCase()) {
            case "CHECKIN-LARVA":
                checkInLarva();
                break;
            case "SUBSCRIBE-WM":
                suscribirseWM();
                break;
            case "COMPRAR-SENSORES":
                comprar();
                break;
            case "ESPERAR-ORDEN":
                Info("Esperando orden");
                in = blockingReceive();
                Info(in.getContent());
                String accion = obtenerResultado();
                hayError = in.getPerformative() != ACLMessage.INFORM;
                if (hayError) {
                    Info(ACLMessage.getPerformative(in.getPerformative())
                            + " Error en mensaje");
                    estado = "CANCEL-WM";
                    break;
                }
                if (accion.equals("turnOff")){
                    estado = "CHECKOUT-LARVA";
                    break;
                }
                else if(accion.equals("")){
                    break;
                }
                else if(accion.equals("login")){
                    estado = "REALIZAR-LOGIN";
                    break;
                }
                else{
                    estado = "CHECKOUT-LARVA";
                    break;
                }
                
            case "REALIZAR-LOGIN":
                realizarLoginWM();
                informarCoachLoginRealizado();
                //moverse_pocho();
                //estado = "ESPERAR-ORDEN";
                estado = "RESCATE-FINALIZADO";
                break;
            case "RESCATE-FINALIZADO":
                Info("Rescate Finalizado");
                
                estado = "CHECKOUT-LARVA";
                break;
            case "CHECKOUT-LARVA":
                checkOutLarva();
                break;
            case "EXIT":
                exit();
                break;
        }
    }
    
    private void moverse_pocho (){
        
        // Crear objeto json
        JsonObject objeto = new JsonObject();

        String accion = "moveF";
        // añadir al objeto
        objeto.add("operation", accion);
        
        out = new ACLMessage();
        out.setSender(getAID());
        out.setConversationId(convID);
        out.setContent(objeto.toString());
        out.setProtocol("REGULAR");
        out.setPerformative(ACLMessage.REQUEST);
        out.addReceiver(new AID(worldManager, AID.ISLOCALNAME));
        out.setInReplyTo(inReplyTo);
        send(out);

        ACLMessage msgRespuesta = blockingReceive();
        String respuesta = msgRespuesta.getContent();
        Info("Respuesta a accion " + accion + ": " + respuesta);
        
        try {
            Thread.sleep(5000);
        } catch (InterruptedException ex) {
            Logger.getLogger(Rescuer.class.getName()).log(Level.SEVERE, null, ex);
        }

    }
    
    
}
