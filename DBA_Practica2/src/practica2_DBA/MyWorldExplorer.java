package practica2_DBA;


import ControlPanel.TTYControlPanel;
import IntegratedAgent.IntegratedAgent;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import com.eclipsesource.json.*;
import static java.lang.Math.abs;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.AbstractMap;

public class MyWorldExplorer extends IntegratedAgent{
    
    //TTYControlPanel myControlPanel;

    String receiver;
    String estado;
    String key = "";
    int width;
    int height;
    int alturaMax;
    JsonArray capabilities;
    JsonArray perceptions;
    ArrayList<String> arrayAcciones = new ArrayList<>();
    ACLMessage ultimoMensaje;
    boolean objetivoAlcanzado = false;

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
        
        //myControlPanel = new TTYControlPanel(getAID());
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
        vector_sensores.add("visual");
        vector_sensores.add("compass");

        // añadir al objeto
        objeto.add("command","login");
        objeto.add("world","World5");
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
        height = objetoRespuesta.get("height").asInt();
        alturaMax = objetoRespuesta.get("maxflight").asInt();
        
        // mostrar respuesta
        Info("Respuesta del servidor: " + respuesta);
        
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
        //myControlPanel.feedData(msgRespuesta,width,height,alturaMax);
        //myControlPanel.fancyShow();
        String respuesta = msgRespuesta.getContent();
        Info("Respuesta del servidor: " + respuesta);
        JsonObject objetoRespuesta = Json.parse(respuesta).asObject();
        JsonArray arrayRespuesta = objetoRespuesta.get("details").asObject().get("perceptions").asArray();
        Info(arrayRespuesta+"");
        
        HashMap<String,JsonArray> mapaSensores = new HashMap<> ();
        for (int i=0; i<arrayRespuesta.size(); i++){
            mapaSensores.put(arrayRespuesta.get(i).asObject().get("sensor").asString(), 
                    arrayRespuesta.get(i).asObject().get("data").asArray());
        }
        return mapaSensores;
    }
    
    /**
    * @author: Pedro Serrano Pérez, Francisco José Molina Sánchez, Jose Armando Albarado Mamani, Miguel Ángel Molina Sánchez
    * @description: Método que permite decir que acción realizar tras leer la información del mundo
    */
    private void tomarDecision(ACLMessage msgRespuesta){
        
        HashMap<String,JsonArray> mapaSensores = leerSensores(msgRespuesta);
        
        double distancia = mapaSensores.get("distance").asArray().get(0).asDouble();
        int alturaDrone = mapaSensores.get("lidar").asArray().get(3).asArray().get(3).asInt();
        int energia = mapaSensores.get("energy").asArray().get(0).asInt();
        int vivo = mapaSensores.get("alive").asArray().get(0).asInt();
        double angular = mapaSensores.get("angular").asArray().get(0).asDouble();
        int anguloDrone = (int) Math.round(mapaSensores.get("compass").asArray().get(0).asDouble());
        int xActual = mapaSensores.get("gps").asArray().get(0).asArray().get(0).asInt();
        int yActual = mapaSensores.get("gps").asArray().get(0).asArray().get(1).asInt();
        int zActual = mapaSensores.get("gps").asArray().get(0).asArray().get(2).asInt();
        ArrayList <ArrayList<Integer>> lidar = new ArrayList<>();
        // crear matriz para lidar
        for (int i=0; i<7; i++){
            lidar.add (new ArrayList <Integer> ());
            for (int j=0;j<7;j++){
                lidar.get(i).add(mapaSensores.get("lidar").asArray().get(i).asArray().get(j).asInt());
            }
        }
        ArrayList <ArrayList<Integer>> visual = new ArrayList<>();
        // crear matriz para visual
        for (int i=0; i<7; i++){
            visual.add (new ArrayList <Integer> ());
            for (int j=0;j<7;j++){
                visual.get(i).add(mapaSensores.get("visual").asArray().get(i).asArray().get(j).asInt());
            }
        }
        Info("vivo "+vivo+"");
        Info("distancia "+distancia+"");
        Info("altura "+alturaDrone+"");
        Info("angulo objetivo "+angular+"");
        Info("anguloDrone "+anguloDrone+"");
       
        if (vivo == 0 || energia == 0){
            estado = "LOGOUT";
        }
        else {
            if (distancia == 0.0){
                bajarAlSuelo(alturaDrone);
                estado = "EJECUTAR_ACCIONES";
                objetivoAlcanzado = true;
            }
            else {  
                if (!comprobarEnergia(energia, alturaDrone)){
                    ArrayList<Double> coste = calcularCoste(visual,angular, zActual);

                    int decision = calcularMejorCoste(coste);

                    calcularAcciones(visual, zActual, decision, anguloDrone);
                }

                estado = "EJECUTAR_ACCIONES";
            }
        }
    }
        
    /**
    * @author: Pedro Serrano Pérez, Francisco José Molina Sánchez, Jose Armando Albarado Mamani, Miguel Ángel Molina Sánchez
    * @description: Método que permite decir que acción realizar tras leer la información del mundo
    */
    private boolean comprobarEnergia(int energia, int alturaDrone){
        boolean necesitaRecargar = false;  
        if (energia <= 270){
            bajarAlSuelo(alturaDrone);
            arrayAcciones.add("recharge");
            necesitaRecargar = true;
        }
        return necesitaRecargar;
    }
    
    /**
    * @author: Pedro Serrano Pérez
    * @description: Devuelve un entero que representa la mejor casilla a la que se puede desplazar el drone
    */
    private int calcularMejorCoste(ArrayList<Double> coste){
        int mejor = -1;
        double mejorCoste = Integer.MAX_VALUE;
        
        for (int i=0; i<coste.size(); i++){
            if (coste.get(i) < mejorCoste){
                mejorCoste = coste.get(i);
                mejor = i;
            }
        }
        return mejor;
    }
    
    /**
    * @author: Pedro Serrano Pérez, Francisco José Molina Sánchez
    * @description: Calcula las acciones que debe realizar el drone en funcion de la decisión tomada, la casilla a la que se desplazará el drone, y la altura y ángulo de este
    */
    private void calcularAcciones(ArrayList <ArrayList<Integer>> visual, int zActual, int decision, int anguloDrone){
        if (decision == 0){
            moverse(visual.get(2).get(3), zActual, -anguloDrone);
        }
        else if (decision == 1){
            moverse(visual.get(2).get(4), zActual, -anguloDrone + 45);
        }
        else if (decision == 2){
            moverse(visual.get(3).get(4), zActual, -anguloDrone + 90);
        }
        else if (decision == 3){
            moverse(visual.get(4).get(4), zActual, -anguloDrone + 135);
        }
        else if (decision == 4){
            moverse(visual.get(4).get(3), zActual, (-anguloDrone - 180)%360);
        }
        else if (decision == 5){
            moverse(visual.get(4).get(2), zActual, -anguloDrone - 135);
        }
        else if (decision == 6){
            moverse(visual.get(3).get(2), zActual, -anguloDrone - 90);
        }
        else if (decision == 7){
            moverse(visual.get(2).get(2), zActual, -anguloDrone - 45);
        }

    }
    
    /**
    * @author: Pedro Serrano Pérez, Francisco José Molina Sánchez
    * @description: Calcula un coste que busca el mejor ángulo para encontrar al objetivo y evita casillas inalcanzables
    */
    private  ArrayList <Double> calcularCoste(ArrayList<ArrayList<Integer>> visual, double angular, int zActual){
        ArrayList <Double> coste = new ArrayList<>();
        
        if (obstaculoAlcanzable(visual.get(2).get(3), zActual)){
            coste.add((double)Math.abs(angular));
        } 
        else{
            coste.add(Double.MAX_VALUE); //No se puede alcanzar
        }
        if (obstaculoAlcanzable(visual.get(2).get(4), zActual)){
            coste.add((double)Math.abs(angular - 45));
        }
        else{
            coste.add(Double.MAX_VALUE); //No se puede alcanzar
        }
        if (obstaculoAlcanzable(visual.get(3).get(4), zActual)){
            coste.add(Math.abs(angular - 90)); 
        } 
        else{
            coste.add(Double.MAX_VALUE); //No se puede alcanzar
        }
        if (obstaculoAlcanzable(visual.get(4).get(4), zActual)){
            coste.add(Math.abs(angular - 135)); 
        } 
        else{
            coste.add(Double.MAX_VALUE); //No se puede alcanzar
        }
        if (obstaculoAlcanzable(visual.get(4).get(3), zActual)){
            coste.add(Math.abs(angular + 180)); 
        } 
        else{
            coste.add(Double.MAX_VALUE); //No se puede alcanzar
        }
        if (obstaculoAlcanzable(visual.get(4).get(2), zActual)){
            coste.add(Math.abs(angular + 135)); 
        } 
        else{
            coste.add(Double.MAX_VALUE); //No se puede alcanzar
        }
        if (obstaculoAlcanzable(visual.get(3).get(2), zActual)){
            coste.add(Math.abs(angular + 90)); 
        } 
        else{
            coste.add(Double.MAX_VALUE); //No se puede alcanzar
        }
        if (obstaculoAlcanzable(visual.get(2).get(2), zActual)){
            coste.add(Math.abs(angular + 45)); 
        } 
        else{
            coste.add(Double.MAX_VALUE); //No se puede alcanzar
        } 
        return coste;
    }
    
    /**
    * @author: Pedro Serrano Pérez, Francisco José Molina Sánchez
    * @description: Una vez decidida la casilla, mueve el drone hasta dicha casilla
    */
    private void moverse(int casilla, int zActual,int giro){
        girar(giro);
        if (casilla - zActual > 0){
            subirAAltura (casilla - zActual);
        }
        
        arrayAcciones.add("moveF");
    }
    
    /**
    * @author: Pedro Serrano Pérez, Francisco José Molina Sánchez
    * @description: Gira el dron un número determinado de grados
    */
    private void girar(int grados){
        for (int i=0; i<Math.abs(grados); i+=45){
            if (grados<0){
                arrayAcciones.add("rotateL");
            }
            else{
                arrayAcciones.add("rotateR");
            }
        }
    }
    
    /**
    * @author: Pedro Serrano Pérez, Francisco José Molina Sánchez, Jose Armando Albarado Mamani, Miguel Ángel Molina Sánchez
    * @description: Comprueba si el dron puede alcanzar una determinada casilla
    */
    private boolean obstaculoAlcanzable(int alturaObstaculo, int zActual){
        boolean alcanzable = alturaObstaculo < alturaMax;
        
        // si el obstáculo está por debajo de la altura máxima, es alcanzable
        if (alcanzable && alturaObstaculo > zActual){
            int vecesASubir = (alturaObstaculo - zActual)/5 + 1; // siempre hay que subir una vez
            int alturaASubir = vecesASubir * 5;
            
            // comprobar que es posible ascender sin superar la altura máxima.
            // para casos en los que el objetivo está cerca del límite
            // y subiendo podemos superar la altura máxima
            alcanzable = alturaMax - (zActual + alturaASubir) > 0;
        }
        return alcanzable;
    }
    
    /**
    * @author: Pedro Serrano Pérez, Francisco José Molina Sánchez, Jose Armando Albarado Mamani, Miguel Ángel Molina Sánchez
    * @description: Añade las acciones necesarias para que el dron se pose en la superficie
    */
    private void bajarAlSuelo(int alturaDrone){
        int veces = alturaDrone/5;
                
        for (int i=0; i<veces; i++){
            arrayAcciones.add("moveD");
        }
        arrayAcciones.add("touchD");
    }
    
    /**
    * @author: Pedro Serrano Pérez, Francisco José Molina Sánchez, Jose Armando Albarado Mamani, Miguel Ángel Molina Sánchez
    * @description: Añade las acciones necesarias para para que el drone suba a una altura determinada
    */
    private void subirAAltura(int alturaObjetivo){
        int veces = alturaObjetivo/5;
                
        for (int i=0; i<veces; i++){
            arrayAcciones.add("moveUP");
        }
        if (alturaObjetivo >= 0 && alturaObjetivo <= 5){
            arrayAcciones.add("moveUP");
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
    * @description: Se ejecuta la acción numAccionActual en el array de acciones arrayAcciones
    */
    private void ejecutarAcciones(){
        Info("Ejecutando acciones");
        
        while (arrayAcciones.size() > 0){
            // Crear objeto json
            JsonObject objeto = new JsonObject();

            String accion = arrayAcciones.get(0);
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
            Info("Respuesta a accion " + accion + ": " + respuesta);
        }
        if (!objetivoAlcanzado){
            estado = "TOMAR_DECISION";
        }
        else{
            estado = "LOGOUT";
        }
        
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
        //myControlPanel.close();
        Info ("Bye");
        _exitRequested = true;  
    }
}
