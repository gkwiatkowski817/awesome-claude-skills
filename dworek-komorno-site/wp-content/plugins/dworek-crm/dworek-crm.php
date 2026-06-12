<?php
/**
 * Plugin Name: Dworek CRM
 * Plugin URI:  https://cukiernia.dworekkomorno.eu/
 * Description: CRM i zarządzanie sprzedażą dla cukierni: zamówienia z pipeline statusów (nowe → potwierdzone → w realizacji → gotowe → dostarczone), kartoteka klientów z historią zakupów, pulpit sprzedaży z podsumowaniem przychodów oraz eksport CSV. Współpracuje z wtyczką Dworek Cake Builder.
 * Version:     1.0.0
 * Author:      Dworek Komorno
 * Text Domain: dworek-crm
 * Requires PHP: 7.4
 */

defined( 'ABSPATH' ) || exit;

define( 'DWK_CRM_VERSION', '1.0.0' );
define( 'DWK_CRM_DIR', plugin_dir_path( __FILE__ ) );
define( 'DWK_CRM_URL', plugin_dir_url( __FILE__ ) );

require_once DWK_CRM_DIR . 'includes/post-types.php';
require_once DWK_CRM_DIR . 'includes/orders.php';
require_once DWK_CRM_DIR . 'includes/admin.php';
require_once DWK_CRM_DIR . 'includes/export.php';

register_activation_hook( __FILE__, function () {
	dwk_crm_register_post_types();
	flush_rewrite_rules();
} );

register_deactivation_hook( __FILE__, 'flush_rewrite_rules' );

/**
 * Style panelu administracyjnego.
 */
add_action( 'admin_enqueue_scripts', function () {
	wp_enqueue_style( 'dwk-crm-admin', DWK_CRM_URL . 'assets/css/admin.css', array(), DWK_CRM_VERSION );
} );
