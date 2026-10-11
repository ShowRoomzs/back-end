package showroomz.api.common.carrier.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import showroomz.api.common.carrier.docs.CommonDeliveryCarrierControllerDocs;
import showroomz.api.common.carrier.dto.DeliveryCarrierResponse;
import showroomz.domain.order.type.DeliveryCarrier;

import java.util.List;

@RestController
@RequestMapping("/v1/common/delivery-carriers")
public class CommonDeliveryCarrierController implements CommonDeliveryCarrierControllerDocs {

    @Override
    @GetMapping
    public List<DeliveryCarrierResponse> getCarriers() {
        return DeliveryCarrier.selectable().stream().map(DeliveryCarrierResponse::of).toList();
    }
}
