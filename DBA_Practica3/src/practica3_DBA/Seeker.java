/*
 * Práctica 3 DBA
 * Grupo ArcelorMittal
 * Curso 2020-2021
 */
package practica3_DBA;

import static ACLMessageTools.ACLMessageTools.getDetailsLARVA;
import static ACLMessageTools.ACLMessageTools.getJsonContentACLM;
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
 * @author Francisco José Molina Sánchez, Pedro Serrano Pérez,
 *         Miguel Ángel Molina Jordán
 */
public class Seeker extends DroneDelMundo{
    final static String TIPO = "SEEKER";
    final int thermalSize = 7;
    
    protected boolean primeraLecturaThermal;
    protected int angulo;
    protected int siguienteCasilla;
    protected ArrayList<ArrayList<Integer>> objetivosEncontrados;
    
    // atributos heredados del AgenteDrone:
    // protected YellowPages yp;
    // protected String estado, servicio, mundo;
    // protected String worldManager, convID;
    // protected boolean hayError;
    // protected ACLMessage in, out;

    @Override
    public void setup() {
        super.setup();
        tipo = "SEEKER"; 
        primeraLecturaThermal = true;
        objetivosEncontrados = new ArrayList();
        angulo = -1;
        siguienteCasilla = -1;
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
            case "LOGIN-WM":
                int posx = 20;
                int posy = 30;
                ArrayList<String> sensores = new ArrayList<>();
                //in = realizarLoginWM(sensores, posx, posy);
                //Info(in.getContent());
                estado = "ESPERAR-ORDEN";
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
                estado = "ESPERAR-ORDEN";
                break;
            
            case "LEER-SENSORES":
                //obtener datos sensores
                break;
            
            case "TRAZAR-RECORRIDO":    
                buscarObjetivo(null, 2, 2);
                break;
            case "CHECKOUT-LARVA":
                checkOutLarva();
                break;
            case "EXIT":
                exit();
                break;
        }
    }
     
    protected void buscarObjetivo(HashMap<String,JsonArray> mapaSensores, int posx, int posy){
         ArrayList <ArrayList<Double>> thermal = new ArrayList<>();
        // crear matriz para thermal
        for (int i=0; i<thermalSize; i++){
            thermal.add (new ArrayList <Double> ());
            for (int j=0;j<thermalSize;j++){
                thermal.get(i).add(mapaSensores.get("thermal").asArray().get(i).asArray().get(j).asDouble());
            }
        }
        
        if(primeraLecturaThermal){
            for (int i = 0; i < thermalSize; i++) {
                for (int j = 0; j < thermalSize; j++) {
                    if(thermal.get(i).get(j) == 0){
                        ArrayList<Integer> arr = new ArrayList();
                        arr.add(posx + j - 3);
                        arr.add(posy + i - 3);
                        objetivosEncontrados.add(arr);
                    }
                }
            }
            if(objetivosEncontrados.size()>0){
                notificarCoachObjetivosEncontrados();
                estado = "ESPERAR-ORDEN";
            }
            primeraLecturaThermal = false;
        }else{
            switch(angulo){
                case 45:
                    //Comprueba la fila superior
                    for (int i = 0; i < thermalSize; i++) {
                        if(thermal.get(0).get(i) == 0){
                        ArrayList<Integer> arr = new ArrayList();
                        arr.add(posx + 0 - 3);
                        arr.add(posy + i - 3);
                        objetivosEncontrados.add(arr);
                        }
                    }
                    //Comprueba la columna de la derecha
                    for (int i = 0; i < thermalSize; i++) {
                        if(thermal.get(i).get(thermalSize-1) == 0){
                        ArrayList<Integer> arr = new ArrayList();
                        arr.add(posx + i - 3);
                        arr.add(posy + thermalSize-1 - 3);
                        objetivosEncontrados.add(arr);
                        }
                    }
                    break;
                case 0:
                    //Comprueba la fila superior
                    for (int i = 0; i < thermalSize; i++) {
                        if(thermal.get(0).get(i) == 0){
                        ArrayList<Integer> arr = new ArrayList();
                        arr.add(posx + 0 - 3);
                        arr.add(posy + i - 3);
                        objetivosEncontrados.add(arr);
                        }
                    }
                    break;
                case 90:
                    //Comprueba la columna de la derecha
                    for (int i = 0; i < thermalSize; i++) {
                        if(thermal.get(i).get(thermalSize-1) == 0){
                        ArrayList<Integer> arr = new ArrayList();
                        arr.add(posx + i - 3);
                        arr.add(posy + thermalSize-1 - 3);
                        objetivosEncontrados.add(arr);
                        }
                    }
                    break;
                case 135:
                    //Comprueba la columna de la derecha
                    for (int i = 0; i < thermalSize; i++) {
                        if(thermal.get(i).get(thermalSize-1) == 0){
                        ArrayList<Integer> arr = new ArrayList();
                        arr.add(posx + i - 3);
                        arr.add(posy + thermalSize-1 - 3);
                        objetivosEncontrados.add(arr);
                        }
                    }
                    
                    //Comprueba la fila inferior
                    for (int i = 0; i < thermalSize; i++) {
                        if(thermal.get(thermalSize-1).get(i) == 0){
                        ArrayList<Integer> arr = new ArrayList();
                        arr.add(posx + thermalSize-1 - 3);
                        arr.add(posy + i - 3);
                        objetivosEncontrados.add(arr);
                        }
                    }
                    break;
                case 180:
                    //Comprueba la fila inferior
                    for (int i = 0; i < thermalSize; i++) {
                        if(thermal.get(thermalSize-1).get(i) == 0){
                        ArrayList<Integer> arr = new ArrayList();
                        arr.add(posx + 0 - 3);
                        arr.add(posy + i - 3);
                        objetivosEncontrados.add(arr);
                        }
                    }
                    break;
                case -135:
                    //Comprueba la fila inferior
                    for (int i = 0; i < thermalSize; i++) {
                        if(thermal.get(thermalSize-1).get(i) == 0){
                        ArrayList<Integer> arr = new ArrayList();
                        arr.add(posx + 0 - 3);
                        arr.add(posy + i - 3);
                        objetivosEncontrados.add(arr);
                        }
                    }
                    
                    //Comprueba la columna de la izquierda
                    for (int i = 0; i < thermalSize; i++) {
                        if(thermal.get(i).get(0) == 0){
                        ArrayList<Integer> arr = new ArrayList();
                        arr.add(posx + 0 - 3);
                        arr.add(posy + i - 3);
                        objetivosEncontrados.add(arr);
                        }
                    }
                    break;
                case -90:
                    //Comprueba la columna de la izquierda
                    for (int i = 0; i < thermalSize; i++) {
                        if(thermal.get(i).get(0) == 0){
                        ArrayList<Integer> arr = new ArrayList();
                        arr.add(posx + 0 - 3);
                        arr.add(posy + i - 3);
                        objetivosEncontrados.add(arr);
                        }
                    }
                    break;
                case -45:
                    //Comprueba la columna de la izquierda
                    for (int i = 0; i < thermalSize; i++) {
                        if(thermal.get(i).get(0) == 0){
                        ArrayList<Integer> arr = new ArrayList();
                        arr.add(posx + 0 - 3);
                        arr.add(posy + i - 3);
                        objetivosEncontrados.add(arr);
                        }
                    }
                    
                    //Comprueba la fila superior
                    for (int i = 0; i < thermalSize; i++) {
                        if(thermal.get(0).get(i) == 0){
                        ArrayList<Integer> arr = new ArrayList();
                        arr.add(posx + 0 - 3);
                        arr.add(posy + i - 3);
                        objetivosEncontrados.add(arr);
                        }
                    }
                    break;
                
            }
            if(objetivosEncontrados.isEmpty()){
                double mejorResultado = 10000;
                ArrayList<Double> res = new ArrayList();
                //Añadimos la esquina superior izquierda
                res.add(thermal.get(0).get(0));
                //Añadimos la parte superior 
                res.add(thermal.get(0).get(thermalSize-1/2));
                //Añadimos la esquina superior dcha
                res.add(thermal.get(0).get(thermalSize-1));
                //Añadimos la parte derecha
                res.add(thermal.get(thermalSize-1/2).get(thermalSize-1));
                //Añadimos la esquina inferior derecha
                res.add(thermal.get(thermalSize-1).get(thermalSize-1));
                //Añadimos la parte de abajo
                res.add(thermal.get(thermalSize-1).get(thermalSize-1/2));
                //Añadimos la esquina inferior izquierda
                res.add(thermal.get(thermalSize-1).get(0));
                //Añadimos la parte izquierda
                res.add(thermal.get(thermalSize-1/2).get(0));
                
                for (int i = 0; i < res.size(); i++) {
                    if(res.get(i)<=mejorResultado){
                        mejorResultado = res.get(i);
                        switch(i){
                            case 0:
                                siguienteCasilla = 7;
                            break;
                            case 1:
                                siguienteCasilla = 0;
                            break;
                            case 2:
                                siguienteCasilla = 1;
                            break;
                            case 3:
                                siguienteCasilla = 2;
                            break;
                            case 4:
                                siguienteCasilla = 3;
                            break;
                            case 5:
                                siguienteCasilla = 4;
                            break;
                            case 6:
                                siguienteCasilla = 5;
                            break;
                            case 7:
                                siguienteCasilla = 6;
                            break;
                        }
                    }
                }
                
                if(thermal.get(0).get(0)<=mejorResultado){
                    mejorResultado = thermal.get(0).get(0);
                    res.add(thermal.get(0).get(0));
                }
            }else{
                notificarCoachObjetivosEncontrados();
                estado = "ESPERAR-ORDEN";
            }
        }
    }
    
    protected void notificarCoachObjetivosEncontrados(){
        JsonObject msg = new JsonObject();
        JsonArray jarr = new JsonArray();
        for (int i = 0; i <objetivosEncontrados.size() ; i++) {
            JsonArray val = new JsonArray();
            val.add(objetivosEncontrados.get(i).get(0));
            val.add(objetivosEncontrados.get(i).get(1));
            jarr.add(val);
        }
        msg.add("objetivos", jarr);
        out = new ACLMessage();
        out.setSender(getAID());
        out.setConversationId(convID);
        out.addReceiver(new AID(coach, AID.ISLOCALNAME));
        out.setContent(msg.toString());
        out.setProtocol("REGULAR");
        out.setPerformative(ACLMessage.INFORM);
        send(out);
    }
}