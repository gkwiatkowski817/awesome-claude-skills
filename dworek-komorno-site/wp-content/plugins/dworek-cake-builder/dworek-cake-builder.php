<?php
/**
 * Plugin Name: Dworek Cake Builder
 * Plugin URI:  https://cukiernia.dworekkomorno.eu/
 * Description: Kreator tortów online — kształt, rozmiar, smak, wykończenie, dekoracje, własna grafika z edytorem (przesuwanie, skalowanie, obracanie), napis, wybór terminu oraz dostawy. Składa zamówienia do modułu Dworek CRM. Shortcode: [dworek_tort_konfigurator]
 * Version:     1.0.0
 * Author:      Dworek Komorno
 * Text Domain: dworek-cake-builder
 * Requires PHP: 7.4
 */

defined( 'ABSPATH' ) || exit;

define( 'DWK_CB_VERSION', '1.0.0' );
define( 'DWK_CB_DIR', plugin_dir_path( __FILE__ ) );
define( 'DWK_CB_URL', plugin_dir_url( __FILE__ ) );

require_once DWK_CB_DIR . 'includes/config.php';
require_once DWK_CB_DIR . 'includes/render.php';
require_once DWK_CB_DIR . 'includes/ajax.php';

/**
 * Rejestracja zasobow frontu (ladowane tylko gdy na stronie jest shortcode).
 */
function dwk_cb_register_assets() {
	wp_register_style( 'dwk-cake-builder', DWK_CB_URL . 'assets/css/builder.css', array(), DWK_CB_VERSION );
	wp_register_script( 'dwk-cake-builder', DWK_CB_URL . 'assets/js/builder.js', array(), DWK_CB_VERSION, true );
	wp_localize_script( 'dwk-cake-builder', 'dwkCakeBuilder', array(
		'ajaxUrl' => admin_url( 'admin-ajax.php' ),
		'nonce'   => wp_create_nonce( 'dwk_cb_order' ),
		'config'  => dwk_cb_get_config(),
		'i18n'    => array(
			'sending'     => __( 'Wysyłanie zamówienia...', 'dworek-cake-builder' ),
			'error'       => __( 'Wystąpił błąd. Spróbuj ponownie lub skontaktuj się z nami telefonicznie.', 'dworek-cake-builder' ),
			'fileTooBig'  => __( 'Plik jest zbyt duży (maks. 8 MB).', 'dworek-cake-builder' ),
			'fileType'    => __( 'Dozwolone formaty: JPG, PNG, WEBP.', 'dworek-cake-builder' ),
			'fillFields'  => __( 'Uzupełnij wymagane pola oznaczone gwiazdką.', 'dworek-cake-builder' ),
			'portions'    => __( 'porcji', 'dworek-cake-builder' ),
			'weight'      => __( 'waga ok.', 'dworek-cake-builder' ),
		),
	) );
}
add_action( 'wp_enqueue_scripts', 'dwk_cb_register_assets' );

/**
 * Shortcode [dworek_tort_konfigurator].
 */
function dwk_cb_shortcode() {
	wp_enqueue_style( 'dwk-cake-builder' );
	wp_enqueue_script( 'dwk-cake-builder' );
	return dwk_cb_render_form();
}
add_shortcode( 'dworek_tort_konfigurator', 'dwk_cb_shortcode' );
