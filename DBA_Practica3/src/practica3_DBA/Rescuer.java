/*
 * Práctica 3 DBA
 * Grupo ArcelorMittal
 * Curso 2020-2021
 */
package practica3_DBA;

import static ACLMessageTools.ACLMessageTools.getDetailsLARVA;
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
    private ArrayList<Integer> posActual;
    private ArrayList<Integer> inicio;
    private static final int costeAccion = 4;
    private int numObjetivosRestantes = 10;

    
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
                }
                if (accion.equals("turnOff")){
                    estado = "CHECKOUT-LARVA";
                }
                else if(accion.equals("")){
                }
                else if(accion.equals("login")){
                    estado = "REALIZAR-LOGIN";
                }
                else if (accion.equals("rescata")){
                    estado = "INICIAR-RESCATE";
                }
                else{
                    estado = "CHECKOUT-LARVA";
                }
                break;
            case "FINALIZAR-COMPRA":
                Info("Envía");
                enviarCoins();
                estado = "ESPERAR-ORDEN";
                break;
            case "REALIZAR-LOGIN":
                cargarMapa();
                actualizarPosicionActual(resultadoComunicacion.get("posx").asInt(), resultadoComunicacion.get("posy").asInt());
                setInicio(resultadoComunicacion.get("posx").asInt(), resultadoComunicacion.get("posy").asInt());
                boolean error = realizarLoginWM();
                informarCoachLoginRealizado();
                if (!error){
                    obtenerDatosSensores();
                    Info("Datos sensores: " + in.getContent());
                    estado = "ESPERAR-ORDEN";
                }
                break;
            case "INICIAR-RESCATE":
                iniciarRescateObjetivos();
                break;
            case "VARIOS-RESCATADOS":
                bajarAlSuelo();
                ejecutarAcciones();
                informarCoachVariosRescatados();
                break;
            case "RESCATE-FINALIZADO":
                Info("Rescate Finalizado");
                estado = "CHECKOUT-LARVA";
                break;
            case "INFORMAR-MUERTE":
                Info ("El drone ha muerto");
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
    
    private ArrayList<ArrayList<Integer>> getVectorObjetivos(JsonArray array){
        ArrayList<ArrayList<Integer>> vector = new ArrayList<ArrayList<Integer>>();
       
        for (int i=0; i< array.size(); i++){
            ArrayList<Integer> posicionObjetivo = new ArrayList<Integer>();
            for (int j=0; j<(array.get(i).asArray()).size(); i++){
                posicionObjetivo.add((array.get(i).asArray()).get(j).asInt());
            }
            vector.add(posicionObjetivo);
        }
        return vector;
    }
    
    private void iniciarRescateObjetivos (){
        ArrayList<ArrayList<Integer>> vectorObjetivos = new ArrayList<>();
        resultadoComunicacion.get("action").asString();
        for (int i=0; i<resultadoComunicacion.get("objetivos").asArray().size(); i++){
            ArrayList<Integer> vectorPosicion = new ArrayList<>();
            for (int j=0; j<resultadoComunicacion.get("objetivos").asArray().get(i).asArray().size(); j++){
                vectorPosicion.add(resultadoComunicacion.get("objetivos").asArray().get(i).asArray().get(j).asInt());
            }
            vectorObjetivos.add(vectorPosicion);
        }
        
        ArrayList<ArrayList<Integer>> ruta= calcularRutaGreedy(vectorObjetivos);
        realizarRuta(ruta);
        ejecutarAcciones();
        //comprobar objetivos restantes
        //if (numObjetivosRestantes == 0){
            estado = "VARIOS-RESCATADOS";
        //}
//        else{
//            estado = "ESPERAR-ORDEN";
//        }
    }
    
    
    //Distancia entre dos puntos
    private double distanciaEntreDosPuntos (int p1X, int p1Y, int p2X, int p2Y){
        int difX = Math.abs(p1X - p2X);
        int difY = Math.abs(p1Y - p2Y);
        double distancia = Math.sqrt(difX*difX + difY*difY);

        return distancia;
    }

    //Calcular la posicion Minima dentro de un array
    private int calcularMinimo(ArrayList<Double> array){
        double minimo = Double.MAX_VALUE;
        int posMin = -1;
        for (int i=0; i<array.size(); i++){
            if (array.get(i) < minimo){
                minimo = array.get(i);
                posMin = i;
            }
        }
        return posMin;
    }
    
    //Calcular ruta
    private ArrayList<ArrayList<Integer>> calcularRutaGreedy (ArrayList<ArrayList<Integer>> vectorObjetivos){
        ArrayList<ArrayList<Integer>> ruta = new ArrayList<ArrayList<Integer>>();
        ArrayList<Integer> actual = new ArrayList<Integer>();
        actual.add(posActual.get(0)); //X
        actual.add(posActual.get(1)); //Y

        while (vectorObjetivos.size() > 0){
            ArrayList<Double> vectorDistancia = new ArrayList<Double>();
            for (int i=0; i<vectorObjetivos.size(); i++){
                vectorDistancia.add(distanciaEntreDosPuntos(actual.get(0), actual.get(1), vectorObjetivos.get(i).get(0), vectorObjetivos.get(i).get(1)));
            }
            int objetivoMasCercano = calcularMinimo(vectorDistancia);
            ruta.add(vectorObjetivos.get(objetivoMasCercano));
            actual.clear();
            actual.add(vectorObjetivos.get(objetivoMasCercano).get(0));
            actual.add(vectorObjetivos.get(objetivoMasCercano).get(1));
            vectorObjetivos.remove(objetivoMasCercano);
        }
        ruta.add(inicio);
        return ruta;
    }

    //Calcular movimientos ruta
    private void realizarRuta (ArrayList<ArrayList<Integer>> ruta){
        ArrayList<Integer> actual = new ArrayList<Integer>();
        actual.add(posActual.get(0)); //X
        actual.add(posActual.get(1)); //Y
        boolean rescatar = true;
        for (int i=0; i<ruta.size(); i++){
            if (i == ruta.size() - 1){
                rescatar = false;
            }
            moverseDePuntoAPunto(actual.get(0), actual.get(1), ruta.get(i).get(0), ruta.get(i).get(1), rescatar);
            actual.clear();
            actual.add(ruta.get(i).get(0));
            actual.add(ruta.get(i).get(1));
        }
    }

    //Calcular movimientos entre dos puntos
    private void moverseDePuntoAPunto (int p1X, int p1Y, int p2X, int p2Y, boolean rescatar){
        
        while (p1X != p2X || p1Y != p2Y){
            if (energia < 250){
                solicitarRecargaACoach();
                in = blockingReceive();
                String accion = obtenerResultado();
                if(accion.equals("recargar")){
                    bajarAlSuelo();
                    if (!iniciarRecarga()){ //No hay error
                        Info("He recargado");
                        energia = 1000;
                    }
                    else{
                        estado = "CHECKOUT-LARVA";
                        break;
                    }
                }
                else if(accion.equals("noRecargar")){
                    estado = "INFORMAR-MUERTE";
                    break;
                }
            }
            else{
                if (p1X < p2X && p1Y < p2Y){
                    nuevoAngulo = 135;
                    p1X ++;
                    p1Y ++;
                } else if (p1X > p2X && p1Y < p2Y){
                    nuevoAngulo = -135;
                    p1X --;
                    p1Y ++;
                } else if (p1X < p2X && p1Y > p2Y){
                    nuevoAngulo = 45;
                    p1X ++;
                    p1Y --;
                } else if (p1X > p2X && p1Y > p2Y){
                    nuevoAngulo = -45;
                    p1X --;
                    p1Y --;
                } else if (p1X < p2X && p1Y == p2Y){
                    nuevoAngulo = 90;
                    p1X ++;
                } else if (p1X > p2X && p1Y == p2Y){
                    nuevoAngulo = -90;
                    p1X --;
                } else if (p1X == p2X && p1Y < p2Y){
                    nuevoAngulo = 180;
                    p1Y ++;
                } else if (p1X == p2X && p1Y > p2Y){
                    nuevoAngulo = 0;
                    p1Y --;
                }

                moverse(p1X, p1Y);
            }
        }

        //bajarAlSuelo(getAlturaActual());

        if(rescatar){
            Info ("Procedo a rescatar objetivos");
            Info("objetivos restantes: " + numObjetivosRestantes);
            arrayAcciones.add("rescue");
            numObjetivosRestantes--;
            //energia = energia - costeAcción; ?????
            //comunicar Rescate Objetivo posxposy
        }
    }
    
    /**
    * @author: Pedro Serrano Pérez, Francisco José Molina Sánchez
    * @description: Gira el dron un número determinado de grados
    */
    protected void girarControlandoEnergia(){
        
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
            energia = energia - 5;
        }
    }

    private void subirAAltura(int alturaObjetivo){
        int veces = alturaObjetivo/5;

        for (int i=0; i<veces; i++){
            arrayAcciones.add("moveUP");
            energia = energia - costeAccion*5;
            zActual = zActual + 5;
        }

        if (alturaObjetivo%5 != 0){
            arrayAcciones.add("moveUP");
            energia = energia - costeAccion*5;
            zActual = zActual + 5;
        }
    }

    private void moverse(int p1X, int p1Y){
        Info ("Me muevo a " + p1X + ", " + p1Y);
        girarControlandoEnergia();
        int alturaCasilla = mapa.getLevel(p1X, p1Y);
        if (alturaCasilla - zActual > 0){
            subirAAltura (alturaCasilla - zActual);
        }
        arrayAcciones.add("moveF");
        energia = energia - costeAccion;
    }
    
    private void actualizarPosicionActual(int posX, int posY){
        posActual = new ArrayList<Integer>();
        posActual.add(posX);
        posActual.add(posY);
    }
    
    private void setInicio(int posX, int posY){
        inicio = new ArrayList<Integer>();
        inicio.add(posX);
        inicio.add(posY);
        
        anguloActualDrone = 90;
        zActual = mapa.getLevel(xActualDrone, yActualDrone);
        energia = 10;
    }
    
    private void informarCoachVariosRescatados () {
        Info ("Estoy en la casilla " + xActualDrone + ", " + yActualDrone);
        out = new ACLMessage();
        out.setSender(getAID());
        out.setConversationId(convID);
        out.addReceiver(new AID(coach, AID.ISLOCALNAME));
        // comprobar objetivos restantes
        if (numObjetivosRestantes == 0){
            estado = "RESCATE-FINALIZADO";
            out.setContent("todosRescatados");
        }
        else{
            estado = "ESPERAR-ORDEN";
            out.setContent("variosRescatados");
        }
        out.setProtocol("REGULAR");
        out.setPerformative(ACLMessage.INFORM);
        send(out);
    }
    
}
