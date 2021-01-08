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
import com.eclipsesource.json.JsonValue;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author Francisco José Molina Sánchez
 */
public abstract class AgenteDrone extends IntegratedAgent{
    
    // protected para que se puedan ver en la subclase
    final static private int TOTAL_OBJETIVOS = 10;
    protected YellowPages yp;
    protected String estado, servicio, mundo;
    protected String worldManager, convID;
    protected boolean hayError;
    protected ACLMessage in, out;
    protected Map2DGrayscale mapa;
    protected int ALTURA_MAX = 256;
    protected JsonObject resultadoComunicacion;
    protected String inReplyTo = "";
    protected String myReply = "Reply2";
    
    @Override
    public void setup()   {
        // Indicamos que es sphinx el identity manager
        // y el único agente conocido     
        super.setup();
        _identitymanager = "Sphinx";
        Info("Realizando setup del agente...");

        // Descripción del grupo
        servicio = "Analytics group ArcelorMittal";
        // Mundo a abrir
        mundo = "World3";
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
        Info("HOLA");
        Info("My ID: " + getAID());
        String a = getAID().toString();
        out = new ACLMessage();
        out.setSender(getAID());
        out.addReceiver(new AID(agente, AID.ISLOCALNAME));
        out.setContent("");
        out.setProtocol("ANALYTICS");
        out.setEncoding(_myCardID.getCardID());
        out.setPerformative(ACLMessage.SUBSCRIBE);
        this.send(out);
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
        //out.setEncoding(_myCardID.getCardID());
        out.setPerformative(ACLMessage.SUBSCRIBE);
        this.send(out);
        return blockingReceive();
    }
    
    // para hacer checkout en LARVA y cancel al World Manager
    protected ACLMessage enviarCancelA(String agente) {
        Info("---------------");
        Info("HACIENDO CANCEL A " + agente);
        String a = getAID().toString();
        Info(a);
        Info("---------------");
        
        out = new ACLMessage();
        out.setConversationId(convID);
        out.setSender(getAID());
        out.addReceiver(new AID(agente, AID.ISLOCALNAME));
        Info("Envio cancelar: " + getAID());
        Info ("Envío a "+ new AID(agente, AID.ISLOCALNAME));
        out.setContent("");
        out.setProtocol("ANALYTICS");
        out.setPerformative(ACLMessage.CANCEL);
        this.send(out);
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

    protected void checkInLarva(){
        Info("Checkin in LARVA with " + _identitymanager);
        in = suscribirseA(_identitymanager); // As seen in slides
        hayError = (in.getPerformative() != ACLMessage.INFORM);
        if (hayError) {
            Info("\t" + ACLMessage.getPerformative(in.getPerformative())
                    + " Checkin failed due to " + getDetailsLARVA(in));
            estado = "EXIT";
        }
        else{
            estado = "SUBSCRIBE-WM";
            Info("\tCheckin ok: " + in.getContent());
        }
    }
    
    protected void checkOutLarva(){
        Info("Exit LARVA");
        in = enviarCancelA(_identitymanager);
        estado = "EXIT";
    }
    
    protected void exit(){
        Info("The agent dies");
        _exitRequested = true;
    }
    

    protected abstract void suscribirseWM();
    
    protected ACLMessage obtenerPreciosTienda(String nombreTienda){
        out = new ACLMessage();
        out.setSender(getAID());
        out.setConversationId(convID);
        out.setContent("{}");
        out.setProtocol("REGULAR");
        out.setPerformative(ACLMessage.QUERY_REF);
        out.addReceiver(new AID(nombreTienda, AID.ISLOCALNAME));
        send(out);
        return blockingReceive();
    }
    
      /**
    * @author: Jose Armando Albarado Mamani
    * @params: T[] array de tiendas
    * @params: sensor es el sensor pendiente a buscar
    * @description: Se busca la referencia del sensor más barato entre las 3 tiendas
    */
    protected JsonValue obtenerMejorPrecioParaSensor(Object T[], String sensor){
        int mejorPrecio = 1000000;
        JsonValue mejorResultado = null;
        for(int i=0;i<3;i++){
            in = obtenerPreciosTienda(T[i].toString());
            JsonObject respuesta = Json.parse(in.getContent()).asObject();
            JsonArray products = respuesta.get("products").asArray();
            for (JsonValue p : products){
                if(p.asObject().get("reference").toString().contains(sensor.toUpperCase())){
                    int precio = Integer.parseInt(p.asObject().get("price").toString());
                    if(precio<=mejorPrecio){
                        mejorPrecio = precio;
                        mejorResultado = p;
                        mejorResultado.asObject().add("tienda", i);
                    }
                }
            }
        }
        return mejorResultado;
    }
    
}
