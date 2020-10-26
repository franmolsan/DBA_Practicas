package practica2_DBA;


import ControlPanel.TTYControlPanel;
import IntegratedAgent.IntegratedAgent;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import com.eclipsesource.json.*;
import java.util.ArrayList;

public class MyWorldExplorer extends IntegratedAgent{
    
    TTYControlPanel myControlPanel;

    String receiver;
    String estado;
    String key = "";
    int width = 100;
    int heigth = 100;
    JsonArray capabilities;
    JsonArray perceptions;
    ArrayList<String> arrayAcciones = new ArrayList<>();
    int numAccionActual = 0;
    String accion_siguiente;

    /**
    * @author: Pedro Serrano Pérez, Francisco José Molina Sánchez, Jose Armando Albarado Mamani, Miguel Ángel Molina Sánchez
    * @description: Definición del setup
    */
    @Override
    public void setup() {
        super.setup();
        doCheckinPlatform();
        doCheckinLARVA();
        receiver = this.whoLarvaAgent();
        _exitRequested = false;
        
        myControlPanel = new TTYControlPanel(getAID());
    }

    /**
    * @author: Pedro Serrano Pérez, Francisco José Molina Sánchez, Jose Armando Albarado Mamani, Miguel Ángel Molina Sánchez
    * @description: Definción del plainExecute, en el que se realizarán todas la gestión del dron
    * 
    */
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

    /**
    * @author: Pedro Serrano Pérez, Francisco José Molina Sánchez, Jose Armando Albarado Mamani, Miguel Ángel Molina Sánchez
    * @description: Definición del takeDown
    */
    @Override
    public void takeDown() {
        this.doCheckoutLARVA();
        this.doCheckoutPlatform();
        super.takeDown();
    }
    
    /**
    * @author: Pedro Serrano mensaje recibido del servidor como respuesta al login que intentamos hacer
    * @description: Se realiza el login al servidor
    * Pérez, Francisco José Molina Sánchez
    * @return: el ACLMessage 
    */
    private ACLMessage realizarLogin(){
        // login
        // Crear objeto json
        JsonObject objeto = new JsonObject();
        JsonArray vector_sensores = new JsonArray();
        vector_sensores.add("alive");
        vector_sensores.add("gps");
        vector_sensores.add("distance");
        vector_sensores.add("angular");
        vector_sensores.add("lidar");
        vector_sensores.add("energy");
        vector_sensores.add("compass");

        // añadir al objeto
        objeto.add("command","login");
        objeto.add("world","Playground1");
        objeto.add("attach", vector_sensores);

        // Serializar objeto en string
        String comando_login = objeto.toString();

        enviarMensajeServidor(comando_login);
        
        ACLMessage msgRespuesta = recibirRespuestaServidor();
        String respuesta = msgRespuesta.getContent();
        Info("Respuesta del servidor: " + respuesta);
        JsonObject objetoRespuesta = Json.parse(respuesta).asObject();
        
        key = objetoRespuesta.get("key").asString();
        capabilities = objetoRespuesta.get("capabilities").asArray();
       /* width = objetoRespuesta.get("width").asInt();
        Info(""+width);
        
        heigth = objetoRespuesta.get("heigth").asInt();
        Info(""+heigth);
        */
        // mostrar respuesta
        Info("Respuesta del servidor: " + respuesta);
        
        cargarAcciones();
        
        return msgRespuesta;
    }
    
    /**
    * @author: Pedro Serrano Pérez, Francisco José Molina Sánchez, Jose Armando Albarado Mamani, Miguel Ángel Molina Sánchez
    * @params: msgRespuseta es el mensaje de respuesta que se recibe del servidor
    * @description: Se procede a leer los sensores partiendo del mensaje de respuesta del servidor al login que hemos realizado anteriormente
    */
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
        myControlPanel.feedData(msgRespuesta,100,100,256);
        myControlPanel.fancyShow();
        String respuesta = msgRespuesta.getContent();
        Info("Respuesta del servidor: " + respuesta);
        
        estado = "SENSORES_LEIDOS";
    }
        
    /**
    * @author: Pedro Serrano Pérez, Francisco José Molina Sánchez, Jose Armando Albarado Mamani, Miguel Ángel Molina Sánchez
    * @params: Mensaje es el mensaje que se envía al servidor
    * @description: Se envía un mensaje al agente en el servidor
    */
    private void enviarMensajeServidor (String mensaje){
        // enviar mensaje al agente en el servidor
        ACLMessage msg = new ACLMessage();
        msg.setSender(getAID());
        msg.addReceiver(new AID(receiver, AID.ISLOCALNAME));
        msg.setContent(mensaje);
        this.send(msg);
    }
    
    /**
    * @author: Pedro Serrano Pérez, Francisco José Molina Sánchez, Jose Armando Albarado Mamani, Miguel Ángel Molina Sánchez
    * @return: ACLMessage el mensaje recibido del servidor como respuesta
    * @description: se devuelve el mensaje recibido del servidor
    */
    private ACLMessage recibirRespuestaServidor (){
        ACLMessage msgReceive = this.blockingReceive();
        return msgReceive;
    }
    
    /**
    * @author: Pedro Serrano Pérez, Francisco José Molina Sánchez, Jose Armando Albarado Mamani, Miguel Ángel Molina Sánchez
    * @params: msgReceive es el mensaje recibido por el servidor
    * @params: contenido es el mensaje de respuesta que deseamos enviar
    * @description: Se crea una respuesta al servidor
    */
    private void responderServidor(ACLMessage msgReceive, String contenido){      
        ACLMessage msg = msgReceive.createReply();
        msg.setContent(contenido);
        this.sendServer(msg); 
    }
    
    /**
    * @author: Pedro Serrano Pérez, Francisco José Molina Sánchez, Jose Armando Albarado Mamani, Miguel Ángel Molina Sánchez
    * @description: Se cargan las acciones que se enviarán al dron
    */
    private void cargarAcciones(){      
        for (int i=0; i<3; i++){
            arrayAcciones.add("rotateL");
        }
        
        for (int i=0; i<10; i++){
            arrayAcciones.add("moveF");
        }
    }
    
    /**
    * @author: Pedro Serrano Pérez, Francisco José Molina Sánchez, Jose Armando Albarado Mamani, Miguel Ángel Molina Sánchez
    * @description: Se ejecuta la acción numAccionActual en el array de acciones arrayAcciones
    */
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
    
    /**
    * @author: José Armando Albarado Mamani
    */
    private void ejecutarLogout(){
        myControlPanel.close();
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
    /**
    * @author: Pedro Serrano Pérez, Francisco José Molina Sánchez, Jose Armando Albarado Mamani, Miguel Ángel Molina Sánchez
    * @description: Se finaliza la conexión con el servidor
    */
    private void ejecutarFin(){
        Info ("Bye");
        _exitRequested = true;  
    }
}
