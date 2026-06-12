<?php
/**
 * Typy tresci CRM: zamowienia (dwk_order) i klienci (dwk_customer)
 * oraz statusy zamowien (pipeline sprzedazy).
 *
 * @package Dworek_CRM
 */

defined( 'ABSPATH' ) || exit;

/**
 * Statusy zamowienia (pipeline): klucz => etykieta.
 */
function dwk_crm_order_statuses() {
	return apply_filters( 'dwk_crm_order_statuses', array(
		'dwk-new'        => __( 'Nowe', 'dworek-crm' ),
		'dwk-confirmed'  => __( 'Potwierdzone', 'dworek-crm' ),
		'dwk-production' => __( 'W realizacji', 'dworek-crm' ),
		'dwk-ready'      => __( 'Gotowe do odbioru', 'dworek-crm' ),
		'dwk-delivered'  => __( 'Zrealizowane', 'dworek-crm' ),
		'dwk-cancelled'  => __( 'Anulowane', 'dworek-crm' ),
	) );
}

function dwk_crm_register_post_types() {
	register_post_type( 'dwk_order', array(
		'labels' => array(
			'name'          => __( 'Zamówienia', 'dworek-crm' ),
			'singular_name' => __( 'Zamówienie', 'dworek-crm' ),
			'add_new_item'  => __( 'Dodaj zamówienie', 'dworek-crm' ),
			'edit_item'     => __( 'Edytuj zamówienie', 'dworek-crm' ),
			'search_items'  => __( 'Szukaj zamówień', 'dworek-crm' ),
			'not_found'     => __( 'Brak zamówień', 'dworek-crm' ),
		),
		'public'       => false,
		'show_ui'      => true,
		'show_in_menu' => 'dwk-crm',
		'supports'     => array( 'title' ),
		'capability_type' => 'post',
		'map_meta_cap' => true,
	) );

	register_post_type( 'dwk_customer', array(
		'labels' => array(
			'name'          => __( 'Klienci', 'dworek-crm' ),
			'singular_name' => __( 'Klient', 'dworek-crm' ),
			'add_new_item'  => __( 'Dodaj klienta', 'dworek-crm' ),
			'edit_item'     => __( 'Edytuj klienta', 'dworek-crm' ),
			'search_items'  => __( 'Szukaj klientów', 'dworek-crm' ),
			'not_found'     => __( 'Brak klientów', 'dworek-crm' ),
		),
		'public'       => false,
		'show_ui'      => true,
		'show_in_menu' => 'dwk-crm',
		'supports'     => array( 'title' ),
		'capability_type' => 'post',
		'map_meta_cap' => true,
	) );

	// Rejestracja statusow zamowien jako post_status.
	foreach ( dwk_crm_order_statuses() as $status => $label ) {
		register_post_status( $status, array(
			'label'                     => $label,
			'public'                    => false,
			'internal'                  => true,
			'show_in_admin_all_list'    => true,
			'show_in_admin_status_list' => true,
			/* translators: %s - liczba zamowien */
			'label_count'               => _n_noop( $label . ' <span class="count">(%s)</span>', $label . ' <span class="count">(%s)</span>' ),
		) );
	}
}
add_action( 'init', 'dwk_crm_register_post_types' );
