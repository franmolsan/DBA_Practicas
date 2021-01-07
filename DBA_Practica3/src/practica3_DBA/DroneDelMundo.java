/*
 * Práctica 3 DBA
 * Grupo ArcelorMittal
 * Curso 2020-2021
 */

package practica3_DBA;


import static ACLMessageTools.ACLMessageTools.getDetailsLARVA;
import ControlPanel.TTYControlPanel;
import IntegratedAgent.IntegratedAgent;
import Map2D.Map2DGrayscale;
import YellowPages.YellowPages;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import com.eclipsesource.json.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class DroneDelMundo extends AgenteDrone{

    protected ArrayList<String> sensoresDrone = new ArrayList<String>();
    private TTYControlPanel myControlPanel;
    private int numVecesThermalPuedeEmpeorarSeguidas = 14;
    private String receiver;
    private String key = "";
    private int width;
    private int height;
    private int alturaMax;
    private JsonArray capabilities;
    private JsonArray perceptions;
    private ACLMessage ultimoMensaje;
    private boolean objetivoAlcanzado = false;
    private boolean rodeoIniciado = false;
    private double thermalInicioRodeo = Double.MAX_VALUE;
    private double distanceAnteriorRodeo = Double.MAX_VALUE;
    private boolean rodeoDecidido = false;
    private boolean rodeoDcha = true;
    private int incrementosThermalSeguidos = 0;

    private ArrayList <ArrayList<Integer>> posicionesPasadas = new ArrayList<>(); // matriz que almacena si has pasado o no por las posiciones
    
    protected String coach = "CerebroComputadora";
    protected String tipo;
    protected int angulo;
    protected HashMap<String,JsonArray> mapaSensores;
    protected double anguloActualDrone;
    protected int xActualDrone;
    protected int yActualDrone;
    protected int zActual;
    protected int energia = 10;
    protected boolean alive = true;
    protected ArrayList<String> arrayAcciones = new ArrayList<>();
    protected int umbralEnergia = 200;
    protected double nuevoAngulo = 0;
    
    ArrayList<String> misCoins = new ArrayList <> ();
    
    /**
    * @author: Pedro Serrano Pérez, Francisco José Molina Sánchez, Jose Armando Albarado Mamani, Miguel Ángel Molina Sánchez
    * @description: Definición del setup
    */
    @Override
    public void setup() {
        super.setup();
        myReply = "myReply";
        //doCheckinPlatform();
        //doCheckinLARVA();
        //receiver = this.whoLarvaAgent();
        //_exitRequested = false;
        
        //myControlPanel = new TTYControlPanel(getAID());
    }

    /**
    * @author: Pedro Serrano Pérez, Francisco José Molina Sánchez, Jose Armando Albarado Mamani, Miguel Ángel Molina Sánchez
    * @description: Definción del plainExecute, en el que se realizarán todas la gestión del dron
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
    * @author: Pedro Serrano Pérez, Francisco José Molina Sánchez 
    * @description: Se realiza el login al servidor
    * mensaje recibido del servidor como respuesta al login que intentamos hacer
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
        vector_sensores.add("energy");
        vector_sensores.add("visual");
        vector_sensores.add("compass");
        vector_sensores.add("thermal");

        // añadir al objeto
        objeto.add("command","login");
        objeto.add("world","World9");
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

        //Inicializar la matriz de posiciones a 0, ya que no has pasado por ninguna
        inicializarMemoria();
        
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
        myControlPanel.feedData(msgRespuesta,width,height,alturaMax);
        myControlPanel.fancyShow();
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
        
        energia = mapaSensores.get("energy").asArray().get(0).asInt();
        int vivo = mapaSensores.get("alive").asArray().get(0).asInt();
        double angular = mapaSensores.get("angular").asArray().get(0).asDouble();
        int anguloDrone = (int) Math.round(mapaSensores.get("compass").asArray().get(0).asDouble());
        int xActual = mapaSensores.get("gps").asArray().get(0).asArray().get(0).asInt();
        xActualDrone = xActual;
        int yActual = mapaSensores.get("gps").asArray().get(0).asArray().get(1).asInt();
        yActualDrone = yActual;
        int zActual = mapaSensores.get("gps").asArray().get(0).asArray().get(2).asInt();

        ArrayList <ArrayList<Integer>> visual = new ArrayList<>();
        // crear matriz para visual
        for (int i=0; i<7; i++){
            visual.add (new ArrayList <Integer> ());
            for (int j=0;j<7;j++){
                visual.get(i).add(mapaSensores.get("visual").asArray().get(i).asArray().get(j).asInt());
            }
        }
        
        int alturaDrone = zActual - visual.get(3).get(3);
                
        ArrayList <ArrayList<Double>> thermal = new ArrayList<>();
        // crear matriz para thermal
        for (int i=0; i<7; i++){
            thermal.add (new ArrayList <Double> ());
            for (int j=0;j<7;j++){
                thermal.get(i).add(mapaSensores.get("thermal").asArray().get(i).asArray().get(j).asDouble());
            }
        }
        
        //Marcar que has pasado por la posicion actual.
        posicionesPasadas.get(yActual).set(xActual, 1);
       
        if (vivo == 0 || energia == 0){
            estado = "LOGOUT";
        }
        else {
            if (distancia == 0.0){
                bajarAlSuelo();
                estado = "EJECUTAR_ACCIONES";
                objetivoAlcanzado = true;
            }
            else {  
                if (!comprobarEnergia(alturaDrone)){
                    
                    int siguientePosicion = calcularSiguientePosicion(angular);
                    ArrayList<Integer> casillaObjetivo = devolverCasillaAlrededor(siguientePosicion);
                    int obstaculo = obstaculoARodear(visual, zActual, anguloDrone);
                    if ((obstaculoAlcanzable(visual.get(casillaObjetivo.get(0)).get(casillaObjetivo.get(1)), zActual) //Puede usar guiado con Angular para ir hacia el objetivo
                        && !casillaRecorrida(siguientePosicion) && !vuelveAtras(siguientePosicion)) || obstaculo == -1){
                        Info("GUIADO ANGULAR");
                        rodeoDecidido = false;
                        rodeoIniciado = false;
                        thermalInicioRodeo = Double.MAX_VALUE;
                        distanceAnteriorRodeo = Double.MAX_VALUE;
                        //calcularAcciones(visual, zActual, siguientePosicion, anguloDrone);
                    }
                    else{ //Rodea el obstáculo por la derecha o por la izquierda
                        Info("GUIADO RODEO");
                        if (vuelveAtras(siguientePosicion) || casillaRecorrida(siguientePosicion)){ //Si la razón para rodear es que no se puede volver atrás, se calcula el obstáculo a rodear
                            siguientePosicion = obstaculo;
                        }
                        siguientePosicion = decidirDireccionRodeo(visual, thermal, zActual, distancia, siguientePosicion);
                        //calcularAcciones(visual, zActual, siguientePosicion, anguloDrone);
                    }
                }
                estado = "EJECUTAR_ACCIONES";
            }
        }
    }
    
    /**
    * @author: Jose Armando Albarado Mamani, Miguel Ángel Molina Sánchez
    * @description: Método que permite inicializar la memoria del mapa
    */
    private void inicializarMemoria(){
        posicionesPasadas.clear();
        for (int i=0; i<height; i++){
            posicionesPasadas.add (new ArrayList <Integer> ());
            for (int j=0;j<width;j++){
                posicionesPasadas.get(i).add(0);
            }
        }
        posicionesPasadas.get(yActualDrone).set(xActualDrone,1);
    }
        
    /**
    * @author: Pedro Serrano Pérez, Francisco José Molina Sánchez, Jose Armando Albarado Mamani, Miguel Ángel Molina Sánchez
    * @description: Método que permite decir que acción realizar tras leer la información del mundo
    */
    private boolean comprobarEnergia(int alturaDrone){
        boolean necesitaRecargar = false;  
        if (energia <= umbralEnergia){
            bajarAlSuelo();
            arrayAcciones.add("recharge");
            necesitaRecargar = true;
        }
        return necesitaRecargar;
    }
    
    /**
    * @author: Pedro Serrano Pérez, Francisco José Molina Sánchez
    * @description: Calcula las acciones que debe realizar el drone en funcion de la decisión tomada, la casilla a la que se desplazará el drone, y la altura y ángulo de este
    */
//    private void calcularAcciones(ArrayList <ArrayList<Integer>> visual, int zActual, int decision, int anguloDrone){
//        if (decision == 0){
//            moverse(visual.get(2).get(3), zActual, -anguloDrone);
//        }
//        else if (decision == 1){
//            moverse(visual.get(2).get(4), zActual, -anguloDrone + 45);
//        }
//        else if (decision == 2){
//            moverse(visual.get(3).get(4), zActual, -anguloDrone + 90);
//        }
//        else if (decision == 3){
//            moverse(visual.get(4).get(4), zActual, -anguloDrone + 135);
//        }
//        else if (decision == 4){
//            moverse(visual.get(4).get(3), zActual, (-anguloDrone - 180)%360);
//        }
//        else if (decision == 5){
//            moverse(visual.get(4).get(2), zActual, -anguloDrone - 135);
//        }
//        else if (decision == 6){
//            moverse(visual.get(3).get(2), zActual, -anguloDrone - 90);
//        }
//        else if (decision == 7){
//            moverse(visual.get(2).get(2), zActual, -anguloDrone - 45);
//        }
//
//    }
    
    /**
    * @author: Pedro Serrano Pérez, Francisco José Molina Sánchez, Jose Armando Albarado Mamani, Miguel Ángel Molina Sánchez
    * @description: Calcula la mejor posición a la que el drone puede ir, teniendo en cuenta el ángulo al objetivo
    */
    private Integer calcularSiguientePosicion(double angular){
        int siguientePosicion = -1;
        double minimo = Double.MAX_VALUE;
        double costeAccion = 0;
        if(yActualDrone > 0){
            costeAccion = Math.abs(angular);
            if (costeAccion < minimo){
                minimo = costeAccion;
                siguientePosicion = 0;
            }
        } 
        
        if(yActualDrone > 0 && xActualDrone < height-1){
            costeAccion = Math.abs(Math.abs(angular - 45));
            if (costeAccion < minimo){
                minimo = costeAccion;
                siguientePosicion = 1;
            }
        }
        
        if(xActualDrone < width-1){
            costeAccion = Math.abs(Math.abs(angular - 90));
            if (costeAccion < minimo){
                minimo = costeAccion;
                siguientePosicion = 2;
            }
        } 
        
        if(yActualDrone < height-1 && xActualDrone < width-1){
            costeAccion = Math.abs(Math.abs(angular - 135));
            if (costeAccion < minimo){
                minimo = costeAccion;
                siguientePosicion = 3;
            }
        }
        
        if(yActualDrone < height-1){
            if (angular > 0){
                costeAccion = Math.abs(Math.abs(angular - 180));
                if (costeAccion < minimo){
                    minimo = costeAccion;
                    siguientePosicion = 4;
                }
            }
            else {
                costeAccion = Math.abs(Math.abs(angular + 180));
                if (costeAccion < minimo){
                    minimo = costeAccion;
                    siguientePosicion = 4;
                }
            }
        } 
        
        if(yActualDrone < height-1 && xActualDrone > 0){
            costeAccion = Math.abs(Math.abs(angular + 135));
            if (costeAccion < minimo){
                minimo = costeAccion;
                siguientePosicion = 5;
            }
        }
        
        if(xActualDrone > 0){
            costeAccion = Math.abs(Math.abs(angular + 90));
            if (costeAccion < minimo){
                minimo = costeAccion;
                siguientePosicion = 6;
            }
        }
        
        if(yActualDrone > 0 && xActualDrone > 0){
            costeAccion = Math.abs(Math.abs(angular + 45));
            if (costeAccion < minimo){
                minimo = costeAccion;
                siguientePosicion = 7;
            }
        } 
   
        return siguientePosicion;
    }
    
     /**
    * @author: Pedro Serrano Pérez, Francisco José Molina Sánchez, Jose Armando Albarado Mamani, Miguel Ángel Molina Sánchez
    * @description: Elige hacia qué dirección rodear el obtáculo
    */
    private Integer decidirDireccionRodeo(ArrayList<ArrayList<Integer>> visual, ArrayList<ArrayList<Double>> thermal, int zActual, double distancia, int casillaDeseada){
        int siguientePosicion = -1;
        int casillaDcha = -1;
        int casillaIzq = -1;
        boolean casillaDchaLibre = false;
        boolean casillaIzqLibre = false;
        
        ArrayList<Integer> casillasProximas = new ArrayList<Integer>();
        casillasProximas.add(visual.get(2).get(3));
        casillasProximas.add(visual.get(2).get(4));
        casillasProximas.add(visual.get(3).get(4));
        casillasProximas.add(visual.get(4).get(4));
        casillasProximas.add(visual.get(4).get(3));
        casillasProximas.add(visual.get(4).get(2));
        casillasProximas.add(visual.get(3).get(2));
        casillasProximas.add(visual.get(2).get(2));
        
        ArrayList<Double> thermalCasillasProximas = new ArrayList<Double>();
        thermalCasillasProximas.add(thermal.get(0).get(3));
        thermalCasillasProximas.add(thermal.get(0).get(6));
        thermalCasillasProximas.add(thermal.get(3).get(6));
        thermalCasillasProximas.add(thermal.get(6).get(6));
        thermalCasillasProximas.add(thermal.get(6).get(3));
        thermalCasillasProximas.add(thermal.get(6).get(0));
        thermalCasillasProximas.add(thermal.get(3).get(0));
        thermalCasillasProximas.add(thermal.get(0).get(0));
        
        if (!rodeoIniciado){ //Cuando inicia el rodeo
            rodeoIniciado = true;
            thermalInicioRodeo = thermal.get(3).get(3);
            distanceAnteriorRodeo = distancia;
            incrementosThermalSeguidos = 0;
            
            //Para rodeo mano Dcha se gira a la izquierda, dejando el obstáculo a la derecha
            for (int i=1; i<casillasProximas.size() && !casillaIzqLibre; i++){
                casillaIzqLibre = obstaculoAlcanzable(casillasProximas.get(((casillaDeseada - i) + casillasProximas.size()) % casillasProximas.size()) , zActual);   
                if (casillaIzqLibre){
                    casillaIzq = ((casillaDeseada - i) + casillasProximas.size()) % casillasProximas.size();
                }
            }
            
            //Para rodeo mano Izq se gira a la derecha, dejando el obstáculo a la izquierda
            for (int i=1; i<casillasProximas.size() && !casillaDchaLibre; i++){
                casillaDchaLibre = obstaculoAlcanzable(casillasProximas.get((casillaDeseada + i) % casillasProximas.size()) , zActual);
                if (casillaDchaLibre){
                    casillaDcha = (casillaDeseada + i) % casillasProximas.size();
                }
            }

            if (thermalCasillasProximas.get(casillaIzq) <= thermalCasillasProximas.get(casillaDcha)){
                siguientePosicion = casillaIzq; 
                rodeoDcha = true;
            }
            else{
                siguientePosicion = casillaDcha;
                 rodeoDcha = false;
            }
            rodeoDecidido = false;
            
        }
        else{
            if (!rodeoDecidido){
                if (thermal.get(3).get(3) > thermalInicioRodeo){ //Si empeora el thermal y no se ha comprometido con una dirección
                    incrementosThermalSeguidos++;
                    if (incrementosThermalSeguidos > numVecesThermalPuedeEmpeorarSeguidas){ //Si ha empeorado 16 veces seguidas cambia la dirección de rodeo
                        rodeoDcha = !rodeoDcha;
                        rodeoDecidido = true;
                        incrementosThermalSeguidos = 0;
                    }
                }
                else if (incrementosThermalSeguidos > 0){ //Si el thermal no empeora
                    incrementosThermalSeguidos=0;
                }
            }
            if (rodeoDcha){
                Info("Rodeo por la derecha");
                for (int i=1; i<casillasProximas.size() && !casillaIzqLibre; i++){
                    casillaIzqLibre = obstaculoAlcanzable(casillasProximas.get(((casillaDeseada - i)+ casillasProximas.size()) % casillasProximas.size()) , zActual);
                    if (casillaIzqLibre){
                        siguientePosicion = ((casillaDeseada - i)+ casillasProximas.size()) % casillasProximas.size();
                    }
                }
            }
            else{
                Info("Rodeo por la izquierda");
                for (int i=1; i<casillasProximas.size() && !casillaDchaLibre; i++){
                    casillaDchaLibre = obstaculoAlcanzable(casillasProximas.get((casillaDeseada + i) % casillasProximas.size()), zActual);
                    if (casillaDchaLibre){
                        siguientePosicion = (casillaDeseada + i) % casillasProximas.size();
                    }
                }
            }
        }
        
        return siguientePosicion;
    }
    
    /**
    * @author: Pedro Serrano Pérez, Francisco José Molina Sánchez
    * @description: Una vez decidida la casilla, mueve el drone hasta dicha casilla
    */
    protected void moverse(){
        girar();
        int alturaCasilla = mapa.getLevel(xActualDrone, yActualDrone);
        if (alturaCasilla - zActual > 0){
            subirAAltura (alturaCasilla - zActual);
        }
        arrayAcciones.add("moveF");
        estado = "LEER-SENSORES";
        ejecutarAcciones();
    }
    
    /**
    * @author: Pedro Serrano Pérez, Francisco José Molina Sánchez
    * @description: Gira el dron un número determinado de grados
    */
    protected void girar(){
        
        double nuevoAnguloPositivo = (nuevoAngulo+360) % 360;
        double anguloActualPositivo = (anguloActualDrone+360) % 360;
        
        double diferenciaAngulos = nuevoAnguloPositivo - anguloActualPositivo;
        double veces = Math.round(Math.abs(diferenciaAngulos)/45);
        
        for (int i=0; i<veces; i++){
            if (diferenciaAngulos < 0){
                anguloActualDrone-=45;
                arrayAcciones.add("rotateL");
            }
            else {
                anguloActualDrone+=45;
                arrayAcciones.add("rotateR");
            }
        }
//        while(Math.round(Math.abs(anguloActualDrone)) != Math.round(Math.abs(nuevoAngulo))){ 
//            Info ("AnguloActual Drone: " +anguloActualDrone);
//            Info ("nuevoAngulo: " +nuevoAngulo);
//            if (Math.abs(anguloActualDrone) - Math.abs(nuevoAngulo) < 0){
//                anguloActualDrone-=45;
//                arrayAcciones.add("rotateL");
//            }
//            else{
//                anguloActualDrone+=45;
//                arrayAcciones.add("rotateR");
//            }
//        }
    }
    
    /**
    * @author: Pedro Serrano Pérez, Francisco José Molina Sánchez, Jose Armando Albarado Mamani, Miguel Ángel Molina Sánchez
    * @description: Comprueba si el dron puede alcanzar una determinada casilla
    */
    private boolean obstaculoAlcanzable(int alturaObstaculo, int zActual){
        boolean alcanzable = alturaObstaculo < alturaMax && alturaObstaculo >= 0;
        //Info("Altura obs: "+ alturaObstaculo);
        //Info("Altura max: "+ alturaMax);
        // si el obstáculo está por debajo de la altura máxima, es alcanzable
        if (alcanzable && alturaObstaculo > zActual){
            int vecesASubir = (alturaObstaculo - zActual)/5; // siempre hay que subir una vez
            
            if ((alturaObstaculo-zActual)%5 != 0){
                vecesASubir += 1;
            }
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
    * @description: Comprueba si una casilla se ha recorrido anteriormente
    */
    private boolean casillaRecorrida(int posicion){
        boolean recorrida = false;

        if(posicion == 0 && yActualDrone > 0){
            recorrida = (posicionesPasadas.get(yActualDrone-1).get(xActualDrone)) == 1;
        } 
        else if(posicion == 1 && yActualDrone > 0 && xActualDrone < width-1){
            recorrida = (posicionesPasadas.get(yActualDrone-1).get(xActualDrone+1)) == 1;
        } 
        else if(posicion == 2 && xActualDrone < width-1){
            recorrida = (posicionesPasadas.get(yActualDrone).get(xActualDrone+1)) == 1;
        } 
        else if(posicion == 3 && yActualDrone < height-1 && xActualDrone < width-1){
            recorrida = (posicionesPasadas.get(yActualDrone+1).get(xActualDrone+1)) == 1;
        } 
        else if(posicion == 4 && yActualDrone < height-1){
            recorrida = (posicionesPasadas.get(yActualDrone+1).get(xActualDrone)) == 1;
        } 
        else if(posicion == 5 && yActualDrone < height-1 && xActualDrone > 0){
            recorrida = (posicionesPasadas.get(yActualDrone+1).get(xActualDrone-1)) == 1;
        } 
        else if(posicion == 6 && xActualDrone > 0){
            recorrida = (posicionesPasadas.get(yActualDrone).get(xActualDrone-1)) == 1;
        } 
        else if(posicion == 7 && yActualDrone > 0 && xActualDrone > 0){
            recorrida = (posicionesPasadas.get(yActualDrone-1).get(xActualDrone-1)) == 1;
        } 
        else {
            recorrida = false;
        }
        return recorrida;
    }
    
    /**
    * @author: Pedro Serrano Pérez, Francisco José Molina Sánchez, Jose Armando Albarado Mamani, Miguel Ángel Molina Sánchez
    * @description: Añade las acciones necesarias para que el dron se pose en la superficie
    */
    protected void bajarAlSuelo(){
        
        if (zActual > mapa.getLevel(xActualDrone, yActualDrone) ){
            Info ("Estoy bajando de zActual :" + zActual + " hasta " + mapa.getLevel(xActualDrone, yActualDrone));
            int veces = zActual/5;

            for (int i=0; i<veces; i++){
                arrayAcciones.add("moveD");
                zActual-=5;
            }

            arrayAcciones.add("touchD");
            zActual = zActual%5;
        }
        
    }
    
    /**
    * @author: Pedro Serrano Pérez, Francisco José Molina Sánchez, Jose Armando Albarado Mamani, Miguel Ángel Molina Sánchez
    * @description: Añade las acciones necesarias para para que el drone suba a una altura determinada
    */
    private void subirAAltura(int alturaObjetivo){
        int veces = alturaObjetivo/5;

        for (int i=0; i<veces; i++){
            arrayAcciones.add("moveUP");
            zActual = zActual + 5;
        }

        if (alturaObjetivo%5 != 0){
            arrayAcciones.add("moveUP");  
            zActual = zActual + 5;
        }
    }
    
    /**
    * @author: Pedro Serrano Pérez, Francisco José Molina Sánchez, Jose Armando Albarado Mamani, Miguel Ángel Molina Sánchez
    * @description: devuelve una casilla en función de la posición que se le pasa por parámetro
    */
    private ArrayList<Integer> devolverCasillaAlrededor(int posicion){
        ArrayList<Integer> casilla = new ArrayList <> ();
        
        if (posicion == 0){
            casilla.add(2);
            casilla.add(3);
        }
        else if (posicion == 1){
            casilla.add(2);
            casilla.add(4);
        }
        else if (posicion == 2){
            casilla.add(3);
            casilla.add(4);
        }
        else if (posicion == 3){
            casilla.add(4);
            casilla.add(4);
        }
        else if (posicion == 4){
            casilla.add(4);
            casilla.add(3);
        }
        else if (posicion == 5){
            casilla.add(4);
            casilla.add(2);
        }
        else if (posicion == 6){
            casilla.add(3);
            casilla.add(2);
        }
        else if (posicion == 7){
            casilla.add(2);
            casilla.add(2);
        }
        return casilla;
    } 
    
    /**
    * @author: Pedro Serrano Pérez, Francisco José Molina Sánchez, Jose Armando Albarado Mamani, Miguel Ángel Molina Sánchez
    * @description: calcula si el drone puede volver atrás por celdas adyacentes
    */
    private boolean vuelveAtras(int casillaDeseada){
        boolean vuelveAtras = false;
        
        if (casillaDeseada == 0){
            vuelveAtras = casillaRecorrida(7) || casillaRecorrida(1) || casillaRecorrida(2) || casillaRecorrida(6);
        }
        else if (casillaDeseada == 1){
            vuelveAtras = casillaRecorrida(0) || casillaRecorrida(2) || casillaRecorrida(7) || casillaRecorrida(3);
        }
        else if (casillaDeseada == 2){
            vuelveAtras = casillaRecorrida(1) || casillaRecorrida(3) || casillaRecorrida(0) || casillaRecorrida(4);
        }
        else if (casillaDeseada == 3){
            vuelveAtras = casillaRecorrida(2) || casillaRecorrida(4) || casillaRecorrida(1) || casillaRecorrida(5);
        }
        else if (casillaDeseada == 4){
            vuelveAtras = casillaRecorrida(3) || casillaRecorrida(5) || casillaRecorrida(6) || casillaRecorrida(2);
        }
        else if (casillaDeseada == 5){
            vuelveAtras = casillaRecorrida(4) || casillaRecorrida(6) || casillaRecorrida(7) || casillaRecorrida(3);
        }
        else if (casillaDeseada == 6){
            vuelveAtras = casillaRecorrida(5) || casillaRecorrida(7) || casillaRecorrida(0) || casillaRecorrida(4);
        }
        else if (casillaDeseada == 7){
            vuelveAtras = casillaRecorrida(6) || casillaRecorrida(0) || casillaRecorrida(1) || casillaRecorrida(5);
        }
   
        return vuelveAtras;
    }
    
     /**
    * @author: Pedro Serrano Pérez, Francisco José Molina Sánchez, Jose Armando Albarado Mamani, Miguel Ángel Molina Sánchez
    * @description: calcula la posición del obstáculo a rodear
    */
    private int obstaculoARodear(ArrayList<ArrayList<Integer>> visual, int zActual, int anguloDrone){
        int obs = -1;
        int angulo = anguloDrone;
        boolean encontrado = false;
        
        if (anguloDrone < 0){
            angulo = angulo + 360;
        }
        
        if (rodeoDcha){ //Si se está rodeando hacia la derecha, se prioriza el siguiente a la derecha
            for (int i=45; i<360 && !encontrado; i+=45){
                if ((angulo+i)%360 == 0){
                    if (!obstaculoAlcanzable(visual.get(2).get(3), zActual)){
                        obs = 0;
                        encontrado = true;
                    }
                }
                else if ((angulo+i)%360 == +45){
                    if (!obstaculoAlcanzable(visual.get(2).get(4), zActual)){
                        obs = 1;
                        encontrado = true;
                    }
                }
                else if ((angulo+i)%360 == +90){
                    if (!obstaculoAlcanzable(visual.get(3).get(4), zActual)){
                        obs = 2;
                        encontrado = true;
                    }
                }
                else if ((angulo+i)%360 == +135){
                    if (!obstaculoAlcanzable(visual.get(4).get(4), zActual)){
                        obs = 3;
                        encontrado = true;
                    }
                }
                else if ((angulo+i)%360 == +180){
                    if (!obstaculoAlcanzable(visual.get(4).get(3), zActual)){
                        obs = 4;
                        encontrado = true;
                    }
                }
                else if ((angulo+i)%360 == +225){
                    if (!obstaculoAlcanzable(visual.get(4).get(2), zActual)){
                        obs = 5;
                        encontrado = true;
                    }
                }
                else if ((angulo+i)%360 == +270){
                    if (!obstaculoAlcanzable(visual.get(3).get(2), zActual)){
                        obs = 6;
                        encontrado = true;
                    }
                }
                else if ((angulo+i)%360 == +315){
                    if (!obstaculoAlcanzable(visual.get(2).get(2), zActual)){
                        obs = 7;
                        encontrado = true;
                    }
                }
            }
        }
        else {
            for (int i=360; i>0 && !encontrado; i-=45){ //Si se está rodeando hacia la izquierda, se prioriza el siguiente a la izquierda
                if ((angulo+i)%360 == 0){
                    if (!obstaculoAlcanzable(visual.get(2).get(3), zActual)){
                        obs = 0;
                        encontrado = true;
                    }
                }
                else if ((angulo+i)%360 == +45){
                    if (!obstaculoAlcanzable(visual.get(2).get(4), zActual)){
                        obs = 1;
                        encontrado = true;
                    }
                }
                else if ((angulo+i)%360 == +90){
                    if (!obstaculoAlcanzable(visual.get(3).get(4), zActual)){
                        obs = 2;
                        encontrado = true;
                    }
                }
                else if ((angulo+i)%360 == +135){
                    if (!obstaculoAlcanzable(visual.get(4).get(4), zActual)){
                        obs = 3;
                        encontrado = true;
                    }
                }
                else if ((angulo+i)%360 == +180){
                    if (!obstaculoAlcanzable(visual.get(4).get(3), zActual)){
                        obs = 4;
                        encontrado = true;
                    }
                }
                else if ((angulo+i)%360 == +225){
                    if (!obstaculoAlcanzable(visual.get(4).get(2), zActual)){
                        obs = 5;
                        encontrado = true;
                    }
                }
                else if ((angulo+i)%360 == +270){
                    if (!obstaculoAlcanzable(visual.get(3).get(2), zActual)){
                        obs = 6;
                        encontrado = true;
                    }
                }
                else if ((angulo+i)%360 == +315){
                    if (!obstaculoAlcanzable(visual.get(2).get(2), zActual)){
                        obs = 7;
                        encontrado = true;
                    }
                }
            }
        }

        return obs;
    }
            
    /**
    * @author: Pedro Serrano Pérez, Francisco José Molina Sánchez, Jose Armando Albarado Mamani, Miguel Ángel Molina Sánchez
    * @params: Mensaje es el mensaje que se envía al servidor
    * @description: Se envía un mensaje al agente en el servidor
    */
    protected void enviarMensajeServidor (String mensaje){
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
    protected ACLMessage recibirRespuestaServidor (){
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
    protected void ejecutarAcciones(){
        Info("Ejecutando acciones");
        boolean estoyVivo = true;
        while (arrayAcciones.size() > 0 && estoyVivo){
            Info ("ejecutao una acción ");
            Info ("me quedan " + arrayAcciones.size() + "acciones");
            // Crear objeto json
            JsonObject objeto = new JsonObject();

            String accion = arrayAcciones.get(0);
            // añadir al objeto
            objeto.add("operation", accion);

            out = new ACLMessage();
            out.setSender(getAID());
            out.setConversationId(convID);
            out.setContent(objeto.toString());
            out.setProtocol("REGULAR");
            out.setPerformative(ACLMessage.REQUEST);
            out.addReceiver(new AID(worldManager, AID.ISLOCALNAME));
            Info ("Reply: " + inReplyTo);
            out.setInReplyTo(inReplyTo);
            send(out);
            arrayAcciones.remove(0);
            Info ("envio :" + out.getContent());
            
            in = blockingReceive();
            Info("recibo: " + in.getContent());
            hayError = in.getPerformative() != ACLMessage.INFORM;
            if (hayError) {
                Info(ACLMessage.getPerformative(in.getPerformative())
                        + " Can't move" + " due to " + getDetailsLARVA(in));
                estoyVivo = false;
                estado = "INFORMAR-MUERTE";
                arrayAcciones.clear();
            }
            else{
                inReplyTo = in.getReplyWith();
            }
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
        myControlPanel.close();
        Info ("Bye");
        _exitRequested = true;  
    }
    
    protected void informarCancelacion(){
        out = new ACLMessage();
        out.setSender(getAID());
        out.setConversationId(convID);
        out.setContent("turnOffCompleted");
        out.setProtocol("REGULAR");
        out.setPerformative(ACLMessage.INFORM);
        out.addReceiver(new AID(coach, AID.ISLOCALNAME));
        send(out);
    }
    
    protected void informarSetupCompletado(){
        out = new ACLMessage();
        out.setSender(getAID());
        out.setConversationId(convID);
        out.setContent("setupCompleted");
        out.setProtocol("REGULAR");
        out.setPerformative(ACLMessage.INFORM);
        out.addReceiver(new AID(coach, AID.ISLOCALNAME));
        send(out);
    }
    
    protected void informarSensorComprado(){
        out = new ACLMessage();
        out.setSender(getAID());
        out.setConversationId(convID);
        out.setContent("sensorOk");
        out.setProtocol("REGULAR");
        out.setPerformative(ACLMessage.INFORM);
        out.addReceiver(new AID(coach, AID.ISLOCALNAME));
        send(out);
    }
    
    protected void cargarMapa(){
        Info ("Cargando mapa");
        JsonObject jsonMapa = resultadoComunicacion.get("jsonMapa").asObject();
        
        mapa = new Map2DGrayscale();
        if (mapa.fromJson(jsonMapa)) {
            Info ("Mapa cargado correctamente");
        }
    }
    
    protected boolean realizarLoginWM(){
        Info("Realizando Login " + in.getContent());
        
        xActualDrone = resultadoComunicacion.get("posx").asInt();
        yActualDrone = resultadoComunicacion.get("posy").asInt();
                
        JsonObject msg = new JsonObject();
        msg.add("operation", "login");
        JsonArray jarr = new JsonArray();
        for(String c:sensoresDrone){
            jarr.add(c);
        }
        msg.add("attach", jarr);
        msg.add("posx", xActualDrone);
        msg.add("posy", yActualDrone);
        
        out = new ACLMessage();
        out.setSender(getAID());
        out.setConversationId(convID);
        out.addReceiver(new AID(worldManager, AID.ISLOCALNAME));
        out.setContent(msg.toString());
        Info ("contenido login: " + out.getContent());
        out.setProtocol("REGULAR");
        //out.setEncoding(_myCardID.getCardID());
        out.setPerformative(ACLMessage.REQUEST);
        out.setInReplyTo(inReplyTo);
        out.setReplyWith(myReply);
        Info ("reply: "+ inReplyTo);
        send(out);
        
        in = blockingReceive();
        hayError = in.getPerformative() != ACLMessage.INFORM;
        if (hayError) {
            Info(ACLMessage.getPerformative(in.getPerformative())
                    + " Could not login" + " due to " + getDetailsLARVA(in));
            estado = "CHECKOUT-LARVA";
        }
        else{
            inReplyTo = in.getReplyWith();
            zActual = mapa.getLevel(xActualDrone, yActualDrone);
        }

        return hayError;
    }
        
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
    * @author: Francisco José Molina Sánchez
    * @params: coins es el array json con los valores de las monedas
    * @description: el drone guarda las monedas que ha recibido del servidor
    */
    protected void guardarCoins(JsonArray coins){
        for (JsonValue c : coins){
            misCoins.add(c.asString());
        }
        Info ("\nMis coins: \n" + misCoins);
    }
    
   
    /**
    * @author: Jose Armando Albarado Mamani
    * @params: T[] array de tiendas, sensor es el sensor pendiente a buscar
    * @description: Se busca la referencia del sensor más barato entre las 3 tiendas
    */
    protected ACLMessage comprarSensor(String referencia, ArrayList<String> payment, String nombreTienda){
        JsonArray jarr = new JsonArray();
        for(String c:payment){
            jarr.add(c);
        }
        JsonObject msg = new JsonObject();
        msg.add("operation", "buy");
        msg.add("reference", referencia);
        msg.add("payment", jarr);
        out = new ACLMessage();
        out.setSender(getAID());
        out.setConversationId(convID);
        out.setContent(msg.toString());
        out.setPerformative(ACLMessage.REQUEST);
        out.setProtocol("REGULAR");
        out.addReceiver(new AID(nombreTienda, AID.ISLOCALNAME));
        send(out);
        return blockingReceive();
    }
    
    protected void suscribirseComo(String tipo) {
        Info("ID: " + convID);
        out = new ACLMessage();
        out.setSender(getAID());
        out.setConversationId(convID);
        out.addReceiver(new AID(worldManager, AID.ISLOCALNAME));
        out.setContent(new JsonObject().add("type", tipo).toString());
        out.setProtocol("REGULAR");
        out.setPerformative(ACLMessage.SUBSCRIBE);
        send(out);
        in = blockingReceive();
        inReplyTo = in.getReplyWith();
        Info("sus reply: " + inReplyTo);
    }
    
    @Override
    protected void checkInLarva(){
        Info("Duermo");
        in = blockingReceive();
        convID = in.getConversationId();
        Info("Despierto");
        super.checkInLarva();
    }
    
    @Override
    protected void checkOutLarva(){
        super.checkOutLarva();
        
        if (!alive){
            informarMuerteACoach();
        }
        else {
            informarCancelacion(); 
        }
    }
    
    @Override
    protected void suscribirseWM(){
        Info("Retrieve who is my WM");
            // First update Yellow Pages
            in = obtenerYP(_identitymanager); // As seen oon slides
            Info("YP obtenidas");
            hayError = in.getPerformative() != ACLMessage.INFORM;
            if (hayError) {
                Info("\t" + ACLMessage.getPerformative(in.getPerformative())
                        + " Query YellowPages failed due to " + getDetailsLARVA(in));
                estado = "CHECKOUT-LARVA";
            }
            else{
                yp = new YellowPages();
                yp.updateYellowPages(in);
                // It might be the case that YP are right but we dont find an appropriate service for us, then leave
                if (yp.queryProvidersofService(servicio).isEmpty()) {
                    Info("\t" + "There is no agent providing the service " + servicio);
                    estado = "CHECKOUT-LARVA";
                }
                else{ // Choose one of the available service providers, i.e., the first one
                    worldManager = yp.queryProvidersofService(servicio).iterator().next();
                    Info(worldManager);
                    // Keep the Conversation ID and spread it amongs the team members
                    // Move on to get the map
                }
            }
                
        suscribirseComo(tipo);
        hayError = in.getPerformative() != ACLMessage.INFORM;
        if (hayError) {
            Info(ACLMessage.getPerformative(in.getPerformative())
                    + " Could not subscribe as " + tipo + " to "
                    + worldManager + " due to " + getDetailsLARVA(in));
            estado = "CANCEL-WM";
        }
        else{
            JsonObject respuesta = Json.parse(in.getContent()).asObject();
            guardarCoins(respuesta.get("coins").asArray());
            Info("YP = " + yp.queryProvidersofService(convID).toString());

            Info("Setup finalizado");
            informarSetupCompletado();
            
            if (tipo.equals("LISTENER")){
                estado = "ESPERAR-ORDEN";
            }
            else{
                Info("Esperando a comprar");
                in = blockingReceive();
                Info("Procediendo a comprar " + in.getContent());
                estado = "COMPRAR-SENSORES";
            }
            
        }   
    }
    
    protected void comprar(){
        String accion = obtenerResultado();
        while (accion.equals("comprar")){
            Object tiendas[] = yp.queryProvidersofService(convID).toArray();
            Map<String, String> resultado  = new HashMap<String, String>();
            resultado.put("Referencia", resultadoComunicacion.get("Referencia").toString());
            resultado.put("Serie", resultadoComunicacion.get("Serie").toString());
            resultado.put("Precio", resultadoComunicacion.get("Precio").toString());
            resultado.put("Tienda", resultadoComunicacion.get("Tienda").toString());
            Info("Datos del mejor sensor:" + resultado.toString());

            ArrayList<String> pago = new ArrayList<>();
            int coinsNecesarias = Integer.parseInt(resultado.get("Precio"));
            if(coinsNecesarias<=misCoins.size()){
                for(int i=0;i<coinsNecesarias;i++){
                    pago.add(misCoins.get(0));
                    misCoins.remove(0);
                }
                in = comprarSensor(resultado.get("Referencia").substring(1, resultado.get("Referencia").length()-1), pago, tiendas[Integer.parseInt(resultado.get("Tienda"))].toString());
                Info ("compra de sensor: " + in.getContent());
            }else{
                Info(" Could not buy from " + resultado.get("Tienda")+
                         " due to not have enough money");
                estado = "CANCEL-WM";
                break;
            }
            JsonValue jsonRespuesta = Json.parse(in.getContent());
            sensoresDrone.add(jsonRespuesta.asObject().get("reference").asString());
            hayError = in.getPerformative() != ACLMessage.INFORM;
            if (hayError) {
                Info(ACLMessage.getPerformative(in.getPerformative())
                        + " Could not buy from " + resultado.get("Tienda")+
                         " due to " + in.getContent());
                estado = "CANCEL-WM";
                break;
            }
            Info("Sensor comprado correctamente"); // + in.getContent());
            informarSensorComprado();
            in = blockingReceive();
            accion = obtenerResultado();    
        }
        if (accion.equals("finalizarCompra")){
            estado = "FINALIZAR-COMPRA";

        }
    }
    
    protected String obtenerResultado(){
        Info("OBTENIENDO RESULTADO " + in.getContent());
        resultadoComunicacion = Json.parse(in.getContent()).asObject();
        String accion = resultadoComunicacion.get("action").asString();
        
        return accion;
    }
    
    protected void informarCoachLoginRealizado(){
        out = new ACLMessage();
        out.setSender(getAID());
        out.setConversationId(convID);
        out.addReceiver(new AID(coach, AID.ISLOCALNAME));
        out.setContent("login completado");
        out.setProtocol("REGULAR");
        out.setPerformative(ACLMessage.INFORM);
        send(out);
    }
    
    protected boolean obtenerDatosSensores(){
        JsonObject objeto = new JsonObject();

        objeto.add("operation", "read");
        out = new ACLMessage();
        out.setSender(getAID());
        out.setConversationId(convID);
        out.setContent(objeto.toString());
        out.setProtocol("REGULAR");
        out.setPerformative(ACLMessage.QUERY_REF);
        out.addReceiver(new AID(worldManager, AID.ISLOCALNAME));
        out.setInReplyTo(inReplyTo);
        send(out);
        in = blockingReceive();
        inReplyTo = in.getReplyWith();
        return in.getPerformative() != ACLMessage.INFORM;
    }
    
    
    protected void actualizarMapaSensores (){
        if (!obtenerDatosSensores()){
            String respuesta = in.getContent();
            Info("Respuesta del servidor: " + respuesta);
            JsonObject objetoRespuesta = Json.parse(respuesta).asObject();
            JsonArray arrayRespuesta = objetoRespuesta.get("details").asObject().get("perceptions").asArray();
            Info(arrayRespuesta+"");
            mapaSensores = new HashMap<>();
            for (int i=0; i<arrayRespuesta.size(); i++){
                mapaSensores.put(arrayRespuesta.get(i).asObject().get("sensor").asString(), 
                        arrayRespuesta.get(i).asObject().get("data").asArray());
            }
        }
        else {
            estado = "CHECKOUT-LARVA";
        }
    }
    
    protected void actualizarValorSensores (){
//        anguloActualDrone = mapaSensores.get("compass").asArray().get(0).asInt();
//        Info ("ANGULO ACTUAL DRONE: " + anguloActualDrone);
        energia = mapaSensores.get("energy").asArray().get(0).asInt();
        if(mapaSensores.get("alive").asArray().get(0).asInt() == 1){
            alive = true;
        }
        else{
            alive = false;
        }
        Info("Energia: " + energia);
    }
    
    protected boolean comprobarAlive(){
        if (!alive){
            estado = "INFORMAR-MUERTE";
        }
        return alive;
    }
    
    protected void informarMuerteACoach(){
        out = new ACLMessage();
        out.setSender(getAID());
        out.setConversationId(convID);
        out.setContent("dead");
        out.setProtocol("REGULAR");
        out.setPerformative(ACLMessage.INFORM);
        out.addReceiver(new AID(worldManager, AID.ISLOCALNAME));
        out.setInReplyTo(inReplyTo);
        send(out);
    }
    
    protected void enviarCoins(){
        JsonObject msg = new JsonObject();
        
        JsonArray coins = new JsonArray();

        for(String c:misCoins){
            coins.add(c);
        }
        msg.add("coins", coins);
        out = new ACLMessage();
        out.setSender(getAID());
        out.setConversationId(convID);
        out.setContent(msg.toString());
        out.setProtocol("REGULAR");
        out.setPerformative(ACLMessage.INFORM);
        out.addReceiver(new AID(coach, AID.ISLOCALNAME));
        send(out);
        
        misCoins = null;
    }

    protected boolean iniciarRecarga(){
        JsonObject msg = new JsonObject();
        msg.add("operation", "recharge");
   
        msg.add("recharge", resultadoComunicacion.get("recarga").asString());
        
        out = new ACLMessage();
        out.setSender(getAID());
        out.setConversationId(convID);
        out.addReceiver(new AID(worldManager, AID.ISLOCALNAME));
        out.setContent(msg.toString());
        out.setProtocol("REGULAR");
        out.setPerformative(ACLMessage.REQUEST);
        out.setInReplyTo(inReplyTo);
        Info ("reply: "+ inReplyTo);
        send(out);
        
        in = blockingReceive();
        hayError = in.getPerformative() != ACLMessage.INFORM;
        if (hayError) {
            Info(ACLMessage.getPerformative(in.getPerformative())
                    + " Could not recharge" + " due to " + getDetailsLARVA(in));
            estado = "CHECKOUT-LARVA";
        }
        else{
            inReplyTo = in.getReplyWith();
        }
        
        return hayError;
    }
    
    protected void solicitarRecargaACoach(){
        out = new ACLMessage();
        out.setSender(getAID());
        out.setConversationId(convID);
        out.addReceiver(new AID(coach, AID.ISLOCALNAME));
        out.setContent("recargar");
        out.setProtocol("REGULAR");
        out.setPerformative(ACLMessage.INFORM);
        send(out);
    }
    
//    // actualiza la z actual y la devuleve
//    protected int getZActual(){
//        zActual = obtener de la radio
//    }
}
