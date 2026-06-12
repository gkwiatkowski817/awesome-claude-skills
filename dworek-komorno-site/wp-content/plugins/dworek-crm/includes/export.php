<?php
/**
 * Eksport zamowien do CSV (przyklad: import do arkusza, ksiegowosc).
 *
 * @package Dworek_CRM
 */

defined( 'ABSPATH' ) || exit;

add_action( 'admin_post_dwk_crm_export', function () {
	if ( ! current_user_can( 'edit_posts' ) ) {
		wp_die( esc_html__( 'Brak uprawnień.', 'dworek-crm' ) );
	}
	check_admin_referer( 'dwk_crm_export', 'nonce' );

	$orders   = get_posts( array(
		'post_type'      => 'dwk_order',
		'post_status'    => array_keys( dwk_crm_order_statuses() ),
		'posts_per_page' => -1,
		'orderby'        => 'date',
		'order'          => 'DESC',
	) );
	$statuses = dwk_crm_order_statuses();

	header( 'Content-Type: text/csv; charset=utf-8' );
	header( 'Content-Disposition: attachment; filename=zamowienia-' . gmdate( 'Y-m-d' ) . '.csv' );

	$out = fopen( 'php://output', 'w' );
	// BOM dla poprawnych znakow w Excelu.
	fwrite( $out, "\xEF\xBB\xBF" );
	fputcsv( $out, array(
		'ID', 'Data zlozenia', 'Termin', 'Status', 'Klient', 'Telefon', 'E-mail',
		'Ksztalt', 'Rozmiar', 'Smak', 'Wykonczenie', 'Dekoracje', 'Napis', 'Okazja',
		'Dostawa', 'Adres', 'Kwota', 'Zrodlo',
	), ';' );

	foreach ( $orders as $order ) {
		$meta = function ( $key ) use ( $order ) {
			return get_post_meta( $order->ID, '_dwk_' . $key, true );
		};
		fputcsv( $out, array(
			$order->ID,
			get_the_date( 'Y-m-d H:i', $order ),
			$meta( 'date' ),
			$statuses[ $order->post_status ] ?? $order->post_status,
			$meta( 'name' ),
			$meta( 'phone' ),
			$meta( 'email' ),
			$meta( 'shape' ),
			$meta( 'size' ),
			$meta( 'flavor' ),
			$meta( 'finish' ),
			implode( ',', (array) $meta( 'extras' ) ),
			$meta( 'inscription' ),
			$meta( 'occasion' ),
			$meta( 'delivery' ),
			$meta( 'address' ),
			number_format( (float) $meta( 'total' ), 2, ',', '' ),
			$meta( 'source' ),
		), ';' );
	}
	fclose( $out );
	exit;
} );
