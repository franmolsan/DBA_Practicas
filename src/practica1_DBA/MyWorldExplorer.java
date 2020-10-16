package practica1_DBA;


import IntegratedAgent.IntegratedAgent;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import com.eclipsesource.json.*;
import java.util.ArrayList;

public class MyWorldExplorer extends IntegratedAgent{

    String receiver;
    String estado;
    String key = "";
    JsonArray capabilities;
    JsonArray perceptions;
    ArrayList<String> arrayAcciones = new ArrayList<>();
    int numAccionActual = 0;
    String accion_siguiente;

    @Override
    public void setup() {
        super.setup();
        doCheckinPlatform();
        doCheckinLARVA();
        receiver = this.whoLarvaAgent();
        _exitRequested = false;

    }

    @Override
    public void plainExecute() {
        ACLMessage mensajeLogin = realizarLogin();
        estado = "LOGIN";
        
            
        while (!_exitRequested){
            switch (estado){
                
                case "LOGIN":
                    leerSensores(mensajeLogin);
                    break;
                    
                case "SENSORES_LEIDOS":
                    ejecutarAccion();
                    break;
                    
                case "LOGOUT":
                    ejecutarLogout();
                    estado = "FIN";
                    break;
                    
                case "FIN":
                    ejecutarFin();
                    break;
                    
                default:
                    estado = "FIN";
                    break;
            }
        }

    }

    @Override
    public void takeDown() {
        this.doCheckoutLARVA();
        this.doCheckoutPlatform();
        super.takeDown();
    }
    
    private ACLMessage realizarLogin(){
        // login
        // Crear objeto json
        JsonObject objeto = new JsonObject();
        JsonArray vector_sensores = new JsonArray();
        vector_sensores.add("alive");
        vector_sensores.add("distance");
        vector_sensores.add("altimeter");

        // añadir al objeto
        objeto.add("command","login");
        objeto.add("world","BasePlayground");
        objeto.add("attach",vector_sensores);

        // Serializar objeto en string
        String comando_login = objeto.toString();

        enviarMensajeServidor(comando_login);
        
        ACLMessage msgRespuesta = recibirRespuestaServidor();
        String respuesta = msgRespuesta.getContent();
        Info("Respuesta del servidor: " + respuesta);
        JsonObject objetoRespuesta = Json.parse(respuesta).asObject();
        
        key = objetoRespuesta.get("key").asString();
        capabilities = objetoRespuesta.get("capabilities").asArray();
        
        // mostrar respuesta
        Info("Respuesta del servidor: " + respuesta);
        
        cargarAcciones();
        
        return msgRespuesta;
    }
    
    private void leerSensores (ACLMessage msgRespuesta){
        // Crear objeto json
        JsonObject objeto = new JsonObject();

        // añadir al objeto
        objeto.add("command","read");
        objeto.add("key", key);

        // Serializar objeto en string
        String comando_leer = objeto.toString();

        
        responderServidor(msgRespuesta, comando_leer);
        
        msgRespuesta = recibirRespuestaServidor();
        String respuesta = msgRespuesta.getContent();
        Info("Respuesta del servidor: " + respuesta);
        
        estado = "SENSORES_LEIDOS";
    }
        
    
    private void enviarMensajeServidor (String mensaje){
        // enviar mensaje al agente en el servidor
        ACLMessage msg = new ACLMessage();
        msg.setSender(getAID());
        msg.addReceiver(new AID(receiver, AID.ISLOCALNAME));
        msg.setContent(mensaje);
        this.send(msg);
    }
    
    private ACLMessage recibirRespuestaServidor (){
        ACLMessage msgReceive = this.blockingReceive();
        return msgReceive;
    }
    
    private void responderServidor(ACLMessage msgReceive, String contenido){      
        ACLMessage msg = msgReceive.createReply();
        msg.setContent(contenido);
        this.sendServer(msg); 
    }
    
    // cargar las acciones necesarias para llegar al objetivo
    private void cargarAcciones(){      
        for (int i=0; i<3; i++){
            arrayAcciones.add("rotateL");
        }
        
        for (int i=0; i<10; i++){
            arrayAcciones.add("moveF");
        }
    }
    
    
    private void ejecutarAccion(){
        Info("Ejecutando accion");
        
        if (numAccionActual < arrayAcciones.size()){
            // Crear objeto json
            JsonObject objeto = new JsonObject();

            // añadir al objeto
            objeto.add("command","execute");
            objeto.add("action", arrayAcciones.get(numAccionActual));
            objeto.add("key", key);
            numAccionActual++;

            // Serializar objeto en string
            String comando_ejecutar = objeto.toString();

            enviarMensajeServidor(comando_ejecutar);

            ACLMessage msgRespuesta = recibirRespuestaServidor();
            String respuesta = msgRespuesta.getContent();
            Info("Respuesta a accion " + respuesta);
        }
        else {
            estado = "LOGOUT";
        }
            
    }
    
    private void ejecutarLogout(){
        Info ("Realizando logout");
         // Crear objeto json
        JsonObject objeto = new JsonObject();

        // añadir al objeto
        objeto.add("command","logout");
        objeto.add("key", key);

        // Serializar objeto en string
        String comando_logout = objeto.toString();

        enviarMensajeServidor(comando_logout);
        
    }
    
    private void ejecutarFin(){
        Info ("Bye");
        _exitRequested = true;
    }

    
}
