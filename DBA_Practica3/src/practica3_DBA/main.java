/*
 * Práctica 3 DBA
 * Grupo ArcelorMittal
 * Curso 2020-2021
 */

package practica3_DBA;

import AppBoot.ConsoleBoot;

public class main {
    static ConsoleBoot _app;
    
    /**
    * @author: Jose Armando Albarado Mamani, Francisco José Molina Sánchez
    * @params: args
     */
    public static void main(String[] args) {
        _app = new ConsoleBoot("PRACTICA3_DBA", args);
        _app.selectConnection();
        
        // primero lanzamos AWACS para visualizar todo
        _app.launchAgent("AWACS", Awacs.class);
        
        // luego lanzamos el resto de agentes
        _app.launchAgent("Cerebro Computadora", Comunicador.class);
        _app.shutDown();
    }
    
}
