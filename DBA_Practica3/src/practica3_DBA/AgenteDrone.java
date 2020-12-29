/*
 * Práctica 3 DBA
 * Grupo ArcelorMittal
 * Curso 2020-2021
 */
package practica3_DBA;

import static ACLMessageTools.ACLMessageTools.getDetailsLARVA;
import static ACLMessageTools.ACLMessageTools.getJsonContentACLM;
import IntegratedAgent.IntegratedAgent;
import Map2D.Map2DGrayscale;
import YellowPages.YellowPages;
import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import java.util.ArrayList;

/**
 *
 * @author Francisco José Molina Sánchez
 */
public class AgenteDrone extends IntegratedAgent{
    
    // protected para que se puedan ver en la subclase
    protected YellowPages yp;
    protected String estado, servicio, mundo;
    protected String worldManager, convID;
    protected boolean hayError;
    protected ACLMessage in, out;
    protected Map2DGrayscale mapa;
    
    
    @Override
    public void setup()   {
        // Indicamos que es sphinx el identity manager
        // y el único agente conocido
        _identitymanager = "Sphinx";
        
        super.setup();

        Info("Realizando setup del agente...");

        // Descripción del grupo
        servicio = "Analytics group ArcelorMittal";
        // Mundo a abrir
        mundo = "World1";
        // Estado inicial del agente
        estado = "CHECKIN-LARVA";
        // Por ahora no existen errores
        hayError = false;

        _exitRequested = false;
    }
    
    @Override
    public void takeDown() {
        Info("Taking down");
        super.takeDown();
    }
    
    
    // para suscribirse a un agente
    // también para hacer el check-in en LARVA (suscribirse a Sphinx)
    // el mensaje que se le pasa es vacío, "".
    protected ACLMessage suscribirseA(String agente) {
        out = new ACLMessage();
        out.setSender(getAID());
        out.addReceiver(new AID(agente, AID.ISLOCALNAME));
        out.setContent("");
        out.setProtocol("ANALYTICS");
        out.setEncoding(_myCardID.getCardID());
        out.setPerformative(ACLMessage.SUBSCRIBE);
        send(out);
        return blockingReceive();
    }
    
    // para suscribirse a un agente
    // también para hacer el check-in en LARVA (suscribirse a Sphinx)
    // se le pasa un mensaje en concreto, p.ej, indicando el tipo de drone
    protected ACLMessage suscribirseA(String agente, String contenidoMensaje) {
        out = new ACLMessage();
        out.setSender(getAID());
        out.addReceiver(new AID(agente, AID.ISLOCALNAME));
        out.setContent(contenidoMensaje);
        out.setProtocol("ANALYTICS");
        out.setEncoding(_myCardID.getCardID());
        out.setPerformative(ACLMessage.SUBSCRIBE);
        send(out);
        return blockingReceive();
    }
    
    // para hacer checkout en LARVA y cancel al World Manager
    protected ACLMessage enviarCancelA(String agente) {
        out = new ACLMessage();
        out.setSender(getAID());
        out.addReceiver(new AID(agente, AID.ISLOCALNAME));
        out.setContent("");
        out.setProtocol("ANALYTICS");
        out.setPerformative(ACLMessage.CANCEL);
        send(out);
        return blockingReceive();
    }
    
    
    // obtener Yellow Pages del identity manager (sphinx)
    // es un QUERY_REF cuya respuesta serán las Yellow Pages (o un error)
    protected ACLMessage obtenerYP(String identityManager) {
        Info ("obteniendo YP de " + identityManager);
        out = new ACLMessage();
        out.setSender(getAID());
        out.addReceiver(new AID(identityManager, AID.ISLOCALNAME));
        out.setProtocol("ANALYTICS");
        out.setContent("");
        out.setPerformative(ACLMessage.QUERY_REF);
        this.send(out);
        return blockingReceive();
    }
    
    
}
