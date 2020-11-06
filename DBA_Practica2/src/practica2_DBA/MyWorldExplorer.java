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
    int height;
    int alturaMax;
    JsonArray capabilities;
    JsonArray perceptions;
    ArrayList<String> arrayAcciones = new ArrayList<>();
    ACLMessage ultimoMensaje;
    boolean objetivoAlcanzado = false;
    boolean rodeoIniciado = false;
    double thermalInicioRodeo = Double.MAX_VALUE;
    double distanceAnteriorRodeo = Double.MAX_VALUE;
    boolean rodeoDecidido = false;
    boolean rodeoDcha = true;
    int incrementosThermalSeguidos = 0;
    
    ArrayList <ArrayList<Integer>> posicionesPasadas = new ArrayList<>(); // matriz que almacena si has pasado o no por las posiciones
    int anguloActualDrone;
    int xActualDrone;
    int yActualDrone;
    
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
        objeto.add("world","Link@Playground1");
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
        
        int energia = mapaSensores.get("energy").asArray().get(0).asInt();
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
                bajarAlSuelo(alturaDrone);
                estado = "EJECUTAR_ACCIONES";
                objetivoAlcanzado = true;
            }
            else {  
                if (!comprobarEnergia(energia, alturaDrone)){
                    
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
                        calcularAcciones(visual, zActual, siguientePosicion, anguloDrone);
                    }
                    else{ //Rodea el obstáculo por la derecha o por la izquierda
                        Info("GUIADO RODEO");
                        if (vuelveAtras(siguientePosicion) || casillaRecorrida(siguientePosicion)){ //Si la razón para rodear es que no se puede volver atrás, se calcula el obstáculo a rodear
                            siguientePosicion = obstaculo;
                        }
                        siguientePosicion = decidirDireccionRodeo(visual, thermal, zActual, distancia, siguientePosicion);
                        calcularAcciones(visual, zActual, siguientePosicion, anguloDrone);
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
            
            //Dcha
            for (int i=1; i<casillasProximas.size() && !casillaDchaLibre; i++){
                casillaDchaLibre = obstaculoAlcanzable(casillasProximas.get(((casillaDeseada - i) + casillasProximas.size()) % casillasProximas.size()) , zActual);   
                if (casillaDchaLibre){
                    casillaDcha = ((casillaDeseada - i) + casillasProximas.size()) % casillasProximas.size();
                }
            }
            
            //Izq
            for (int i=1; i<casillasProximas.size() && !casillaIzqLibre; i++){
                casillaIzqLibre = obstaculoAlcanzable(casillasProximas.get((casillaDeseada + i) % casillasProximas.size()) , zActual);
                if (casillaIzqLibre){
                    casillaIzq = (casillaDeseada + i) % casillasProximas.size();
                }
            }

            if (thermalCasillasProximas.get(casillaDcha) <= thermalCasillasProximas.get(casillaIzq)){
                siguientePosicion = casillaDcha;
                rodeoDcha = true;
            }
            else{
                siguientePosicion = casillaIzq;
                 rodeoDcha = false;
            }
            rodeoDecidido = false;
            
        }
        else{
            if (!rodeoDecidido){
                if (thermal.get(3).get(3) > thermalInicioRodeo){ //Si empeora el thermal y no se ha comprometido con una dirección
                    incrementosThermalSeguidos++;
                    if (incrementosThermalSeguidos > 16){ //Si ha empeorado 16 veces seguidas cambia la dirección de rodeo
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
                for (int i=1; i<casillasProximas.size() && !casillaDchaLibre; i++){
                    casillaDchaLibre = obstaculoAlcanzable(casillasProximas.get(((casillaDeseada - i)+ casillasProximas.size()) % casillasProximas.size()) , zActual);
                    if (casillaDchaLibre){
                        siguientePosicion = ((casillaDeseada - i)+ casillasProximas.size()) % casillasProximas.size();
                    }
                }
            }
            else{
                Info("Rodeo por la izquierda");
                for (int i=1; i<casillasProximas.size() && !casillaIzqLibre; i++){
                    casillaIzqLibre = obstaculoAlcanzable(casillasProximas.get((casillaDeseada + i) % casillasProximas.size()), zActual);
                    if (casillaIzqLibre){
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
        
        if (alturaObjetivo%5 != 0){
            arrayAcciones.add("moveUP");
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
            objeto.add("action", accion);
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
        myControlPanel.close();
        Info ("Bye");
        _exitRequested = true;  
    }
}
