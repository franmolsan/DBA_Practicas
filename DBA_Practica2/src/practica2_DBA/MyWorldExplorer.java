package practica2_DBA;


import ControlPanel.TTYControlPanel;
import IntegratedAgent.IntegratedAgent;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import com.eclipsesource.json.*;
import java.util.ArrayList;
import java.util.HashMap;

public class MyWorldExplorer extends IntegratedAgent{
    
    TTYControlPanel myControlPanel;

    String receiver;
    String estado;
    String key = "";
    int width;
    int heigth;
    int alturaMax;
    JsonArray capabilities;
    JsonArray perceptions;
    ArrayList<String> arrayAcciones = new ArrayList<>();
    ACLMessage ultimoMensaje;
    boolean objetivoAlcanzando = false;

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
        ultimoMensaje = realizarLogin();
        estado = "TOMAR_DECISION";
        
        while (!_exitRequested){
            switch (estado){
                
                case "TOMAR_DECISION":
                    tomarDecision(ultimoMensaje);
                    break;
                    
                case "EJECUTAR_ACCIONES":
                    ejecutarAcciones();
                    break;
                    
                case "OBJETIVO_ALCANZADO":
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
        width = objetoRespuesta.get("width").asInt();
        heigth = objetoRespuesta.get("heigth").asInt();
        alturaMax = objetoRespuesta.get("maxflight").asInt();
        
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
    private HashMap<String,JsonArray> leerSensores (ACLMessage msgRespuesta){
        // Crear objeto json
        JsonObject objeto = new JsonObject();

        // añadir al objeto
        objeto.add("command","read");
        objeto.add("key", key);

        // Serializar objeto en string
        String comando_leer = objeto.toString();

        
        responderServidor(msgRespuesta, comando_leer);
        
        msgRespuesta = recibirRespuestaServidor();
        myControlPanel.feedData(msgRespuesta,width,heigth,256);
        myControlPanel.fancyShow();
        String respuesta = msgRespuesta.getContent();
        Info("Respuesta del servidor: " + respuesta);
        JsonObject objetoRespuesta = Json.parse(respuesta).asObject();
        JsonArray arrayRespuesta = objetoRespuesta.get("details").asObject().get("perceptions").asArray();
        Info(arrayRespuesta+"");
        estado = "BUSCANDO_OBJETIVO";
        
        HashMap<String,JsonArray> mapaSensores = new HashMap<> ();
        for (int i=0; i<arrayRespuesta.size(); i++){
            mapaSensores.put(arrayRespuesta.get(i).asObject().get("sensor").asString(), 
                    arrayRespuesta.get(i).asObject().get("data").asArray());
        }
        return mapaSensores;
    }
    
    private void tomarDecision(ACLMessage msgRespuesta){
        HashMap<String,JsonArray> mapaSensores = leerSensores(msgRespuesta);
        
        double distancia = mapaSensores.get("distance").asArray().get(0).asDouble();
        int alturaDrone = mapaSensores.get("lidar").asArray().get(3).asArray().get(3).asInt();
        int energia = mapaSensores.get("energy").asArray().get(0).asInt();
        int vivo = mapaSensores.get("alive").asArray().get(0).asInt();
        double angular = mapaSensores.get("angular").asArray().get(0).asDouble();
        int xActual = mapaSensores.get("gps").asArray().get(0).asInt();
        int yActual = mapaSensores.get("gps").asArray().get(1).asInt();
        int zActual = mapaSensores.get("gps").asArray().get(2).asInt();
        ArrayList <ArrayList<Integer>> lidar = new ArrayList<>();
        
        // crear matriz para lidar
        for (int i=0; i<7; i++){
            lidar.add (new ArrayList <Integer> ());
            for (int j=0;j<7;j++){
                lidar.get(i).add(mapaSensores.get("lidar").asArray().get(i).asArray().get(j).asInt());
            }
        }
        
        Info("distancia "+distancia+"");
        Info("altura "+alturaDrone+"");
       
        if (vivo == 0){
            estado = "LOGOUT";
        }
        
        /*
        // estamos en la casilla del objetivo
        if (distancia == 0.0){
            
            // si estamos en el suelo, ya hemos rescatado a Ludwig
            if (alturaDrone == 0){
                objetivoAlcanzando = true;
            }
            
            // si no estamos en el suelo, debemos bajar el número de veces necesario
            else {
                bajarAlSuelo(alturaDrone);
            }
        }
        */
        
        else {
            if (energia <= 350){
                bajarAlSuelo(alturaDrone);
                arrayAcciones.add("recharge");
            }
            
            else {
                if (distancia <= 3.0){
                    if (angular == 0.0){
                        if (-lidar.get(2).get(3) + zActual < alturaMax){
                            subirAAltura (-lidar.get(2).get(3) - alturaDrone);
                            arrayAcciones.add("moveF");
                            if (-lidar.get(1).get(3) + zActual < alturaMax){
                                if (-lidar.get(0).get(3) + zActual < alturaMax){
                                    
                                }
                            }
                        }
                    }
                }
            }
            
            estado = "EJECUTAR_ACCIONES";
        }
        
        
    }
        
    private void bajarAlSuelo(int alturaDrone){
        int veces = alturaDrone/5;
                
        for (int i=0; i<veces; i++){
            arrayAcciones.add("moveD");
        }
        arrayAcciones.add("touchD");
    }
    
    private void subirAAltura(int alturaObjetivo){
        int veces = alturaObjetivo/5;
                
        for (int i=0; i<veces; i++){
            arrayAcciones.add("moveUp");
        }
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
    private void ejecutarAcciones(){
        Info("Ejecutando accion");
        
        while (arrayAcciones.size() > 0){
            // Crear objeto json
            JsonObject objeto = new JsonObject();

            // añadir al objeto
            objeto.add("command","execute");
            objeto.add("action", arrayAcciones.get(0));
            arrayAcciones.remove(0);
            objeto.add("key", key);

            // Serializar objeto en string
            String comando_ejecutar = objeto.toString();

            enviarMensajeServidor(comando_ejecutar);

            ACLMessage msgRespuesta = recibirRespuestaServidor();
            String respuesta = msgRespuesta.getContent();
            Info("Respuesta a accion " + respuesta);
        }
            
    }
    
    private void ejecutarRecarga(){
        
    }
    
    /**
    * @author: José Armando Albarado Mamani
    */
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
    /**
    * @author: Pedro Serrano Pérez, Francisco José Molina Sánchez, Jose Armando Albarado Mamani, Miguel Ángel Molina Sánchez
    * @description: Se finaliza la conexión con el servidor
    */
    private void ejecutarFin(){
        myControlPanel.close();
        Info ("Bye");
        _exitRequested = true;  
    }
}
