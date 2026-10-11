package br.com.romaneio.util;

public final class GeoUtils {

    private static final double RAIO_TERRA_KM = 6371.0;
    // Fator de correção viária média (curvatura e traçado das ruas em relação à linha reta geodésica)
    public static final double FATOR_MALHA_VIARIA = 1.25;
    // Velocidade média urbana para entregas (km/h)
    public static final double VELOCIDADE_MEDIA_KMH = 25.0;
    // Tempo médio de atendimento por parada (minutos)
    public static final int TEMPO_ATENDIMENTO_POR_PARADA_MINUTOS = 3;

    private GeoUtils() {}

    /**
     * Calcula a distância geodésica (Haversine) entre dois pontos em quilômetros,
     * aplicando fator de correção para malha viária urbana.
     */
    public static double calcularDistanciaKm(double lat1, double lon1, double lat2, double lon2) {
        if (Double.compare(lat1, lat2) == 0 && Double.compare(lon1, lon2) == 0) {
            return 0.0;
        }

        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double radLat1 = Math.toRadians(lat1);
        double radLat2 = Math.toRadians(lat2);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.sin(dLon / 2) * Math.sin(dLon / 2) * Math.cos(radLat1) * Math.cos(radLat2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        double distanciaLinhaReta = RAIO_TERRA_KM * c;
        return distanciaLinhaReta * FATOR_MALHA_VIARIA;
    }

    public static String formatarDistancia(double distanciaKm) {
        return String.format(java.util.Locale.US, "%.2f km", distanciaKm);
    }

    public static int estimarTempoMinutos(double distanciaKm, int totalParadas) {
        double tempoDeslocamentoMinutos = (distanciaKm / VELOCIDADE_MEDIA_KMH) * 60.0;
        int tempoParadas = totalParadas * TEMPO_ATENDIMENTO_POR_PARADA_MINUTOS;
        return (int) Math.round(tempoDeslocamentoMinutos + tempoParadas);
    }

    public static String formatarTempo(int minutos) {
        if (minutos < 60) {
            return minutos + " min";
        }
        int horas = minutos / 60;
        int restoMinutos = minutos % 60;
        if (restoMinutos == 0) {
            return horas + "h";
        }
        return horas + "h " + restoMinutos + "min";
    }
}
