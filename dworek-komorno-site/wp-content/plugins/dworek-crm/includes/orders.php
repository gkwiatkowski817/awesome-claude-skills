<?php
/**
 * Logika zamowien i klientow:
 * - dwk_crm_create_order() - tworzy zamowienie (uzywane przez Dworek Cake Builder),
 * - dopasowanie / utworzenie klienta po adresie e-mail,
 * - agregaty na potrzeby pulpitu sprzedazy.
 *
 * @package Dworek_CRM
 */

defined( 'ABSPATH' ) || exit;

/**
 * Tworzy zamowienie wraz z powiazanym klientem.
 *
 * @param array $data Dane zamowienia (patrz Dworek Cake Builder, includes/ajax.php).
 * @return int|WP_Error ID zamowienia.
 */
function dwk_crm_create_order( array $data ) {
	$customer_id = dwk_crm_upsert_customer( array(
		'name'    => $data['name'] ?? '',
		'email'   => $data['email'] ?? '',
		'phone'   => $data['phone'] ?? '',
		'address' => $data['address'] ?? '',
	) );

	$order_id = wp_insert_post( array(
		'post_type'   => 'dwk_order',
		'post_status' => 'dwk-new',
		'post_title'  => sprintf(
			'%s — %s — %s',
			$data['name'] ?? __( 'Klient', 'dworek-crm' ),
			$data['date'] ?? '',
			$data['occasion'] ?? ''
		),
	), true );

	if ( is_wp_error( $order_id ) ) {
		return $order_id;
	}

	$meta = array(
		'_dwk_customer_id' => $customer_id,
		'_dwk_shape'       => $data['shape'] ?? '',
		'_dwk_size'        => $data['size'] ?? '',
		'_dwk_flavor'      => $data['flavor'] ?? '',
		'_dwk_finish'      => $data['finish'] ?? '',
		'_dwk_extras'      => $data['extras'] ?? array(),
		'_dwk_inscription' => $data['inscription'] ?? '',
		'_dwk_occasion'    => $data['occasion'] ?? '',
		'_dwk_notes'       => $data['notes'] ?? '',
		'_dwk_delivery'    => $data['delivery'] ?? '',
		'_dwk_date'        => $data['date'] ?? '',
		'_dwk_address'     => $data['address'] ?? '',
		'_dwk_name'        => $data['name'] ?? '',
		'_dwk_phone'       => $data['phone'] ?? '',
		'_dwk_email'       => $data['email'] ?? '',
		'_dwk_photo_id'    => (int) ( $data['photo_id'] ?? 0 ),
		'_dwk_transform'   => $data['transform'] ?? '',
		'_dwk_total'       => (float) ( $data['total'] ?? 0 ),
		'_dwk_source'      => $data['source'] ?? 'reczne',
	);
	foreach ( $meta as $key => $value ) {
		update_post_meta( $order_id, $key, $value );
	}

	do_action( 'dwk_crm_order_created', $order_id, $data );

	return $order_id;
}

/**
 * Znajduje klienta po e-mailu lub tworzy nowego; aktualizuje dane kontaktowe.
 *
 * @return int ID klienta (0 gdy brak e-maila).
 */
function dwk_crm_upsert_customer( array $data ) {
	$email = sanitize_email( $data['email'] ?? '' );
	if ( ! $email ) {
		return 0;
	}

	$existing = get_posts( array(
		'post_type'      => 'dwk_customer',
		'post_status'    => 'any',
		'posts_per_page' => 1,
		'fields'         => 'ids',
		'meta_key'       => '_dwk_email',
		'meta_value'     => $email,
	) );

	if ( $existing ) {
		$customer_id = (int) $existing[0];
	} else {
		$customer_id = wp_insert_post( array(
			'post_type'   => 'dwk_customer',
			'post_status' => 'publish',
			'post_title'  => $data['name'] ?: $email,
		) );
		if ( is_wp_error( $customer_id ) ) {
			return 0;
		}
		update_post_meta( $customer_id, '_dwk_email', $email );
		update_post_meta( $customer_id, '_dwk_first_order', current_time( 'mysql' ) );
	}

	// Aktualizacja danych kontaktowych przy kazdym zamowieniu.
	if ( ! empty( $data['phone'] ) ) {
		update_post_meta( $customer_id, '_dwk_phone', sanitize_text_field( $data['phone'] ) );
	}
	if ( ! empty( $data['address'] ) ) {
		update_post_meta( $customer_id, '_dwk_address', sanitize_text_field( $data['address'] ) );
	}
	update_post_meta( $customer_id, '_dwk_last_order', current_time( 'mysql' ) );

	return $customer_id;
}

/**
 * Zamowienia danego klienta (historia zakupow).
 */
function dwk_crm_get_customer_orders( $customer_id ) {
	return get_posts( array(
		'post_type'      => 'dwk_order',
		'post_status'    => array_keys( dwk_crm_order_statuses() ),
		'posts_per_page' => -1,
		'meta_key'       => '_dwk_customer_id',
		'meta_value'     => (int) $customer_id,
		'orderby'        => 'date',
		'order'          => 'DESC',
	) );
}

/**
 * Agregaty sprzedazy do pulpitu: liczby zamowien wg statusu,
 * przychod (zamowienia niezanulowane) w biezacym miesiacu i ogolem.
 */
function dwk_crm_sales_stats() {
	global $wpdb;

	$stats = array(
		'by_status'     => array(),
		'revenue_month' => 0.0,
		'revenue_total' => 0.0,
		'orders_month'  => 0,
		'customers'     => (int) wp_count_posts( 'dwk_customer' )->publish,
	);

	foreach ( dwk_crm_order_statuses() as $status => $label ) {
		$stats['by_status'][ $status ] = (int) $wpdb->get_var( $wpdb->prepare(
			"SELECT COUNT(*) FROM {$wpdb->posts} WHERE post_type = 'dwk_order' AND post_status = %s",
			$status
		) );
	}

	$month_start = gmdate( 'Y-m-01 00:00:00' );
	$revenue_sql = "SELECT COALESCE(SUM(pm.meta_value+0), 0)
		FROM {$wpdb->posts} p
		INNER JOIN {$wpdb->postmeta} pm ON pm.post_id = p.ID AND pm.meta_key = '_dwk_total'
		WHERE p.post_type = 'dwk_order' AND p.post_status NOT IN ('dwk-cancelled', 'trash')";

	$stats['revenue_total'] = (float) $wpdb->get_var( $revenue_sql );
	$stats['revenue_month'] = (float) $wpdb->get_var( $wpdb->prepare( $revenue_sql . ' AND p.post_date >= %s', $month_start ) );
	$stats['orders_month']  = (int) $wpdb->get_var( $wpdb->prepare(
		"SELECT COUNT(*) FROM {$wpdb->posts} WHERE post_type = 'dwk_order' AND post_status NOT IN ('dwk-cancelled', 'trash') AND post_date >= %s",
		$month_start
	) );

	return $stats;
}

/**
 * Powiadomienie e-mail do klienta przy zmianie statusu zamowienia.
 */
function dwk_crm_notify_status_change( $new_status, $old_status, $post ) {
	if ( 'dwk_order' !== $post->post_type || $new_status === $old_status ) {
		return;
	}
	$statuses = dwk_crm_order_statuses();
	if ( ! isset( $statuses[ $new_status ] ) || 'dwk-new' === $new_status ) {
		return;
	}
	$email = get_post_meta( $post->ID, '_dwk_email', true );
	if ( ! is_email( $email ) ) {
		return;
	}
	wp_mail(
		$email,
		sprintf( __( 'Zamówienie nr %d — zmiana statusu', 'dworek-crm' ), $post->ID ),
		sprintf(
			/* translators: 1: numer zamowienia, 2: status */
			__( "Status Twojego zamówienia nr %1\$d został zmieniony na: %2\$s.\n\nCukiernia Dworek Komorno", 'dworek-crm' ),
			$post->ID,
			$statuses[ $new_status ]
		)
	);
}
add_action( 'transition_post_status', 'dwk_crm_notify_status_change', 10, 3 );
